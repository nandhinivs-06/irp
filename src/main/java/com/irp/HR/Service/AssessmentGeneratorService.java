package com.irp.HR.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Exception.AssessmentGenerationException;
import com.irp.HR.Model.DifficultyLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates MCQ questions via Ollama /api/generate.
 *
 * Difficulty is now driven by BOTH the DifficultyLevel enum AND the actual
 * years of experience — the model is told explicitly what kind of question
 * each tier means (definitions vs applied scenarios vs system-design /
 * advanced coding), instead of just seeing the word "HARD" and guessing.
 *
 * Prompt format: explicit rules + a single full example object, no dangling
 * "continue this partial JSON" prefix trick (small models like llama3.2
 * tend to ignore that and fall back to plain question strings instead of
 * objects — that was the root cause of "array has N elements, parsed 0").
 *
 * Falls back to a static question bank if the model fails, so the
 * assessment is never blocked.
 */
@Service
public class AssessmentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentGeneratorService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    private static final int QUESTION_COUNT = 20;
    private static final int MIN_ACCEPTABLE  = 3;
    private static final int MAX_RETRIES     = 2;

    public AssessmentGeneratorService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3}") String model
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);
        factory.setReadTimeout(180_000);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.model = model;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Public entry point
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * @param yearsOfExperience actual years pulled from the candidate's resume
     *                          (nullable — treated as a fresher/0 if absent).
     *                          Used to give the model concrete calibration
     *                          instead of just the difficulty label.
     */
    public List<GeneratedQuestion> generateQuestions(
            String jobTitle, String jobDescription, DifficultyLevel difficulty, Integer yearsOfExperience) {

        // Truncate description to keep prompt inside context window
        String desc = (jobDescription == null ? "" : jobDescription);
        if (desc.length() > 300) desc = desc.substring(0, 300) + "…";

        int years = yearsOfExperience != null ? yearsOfExperience : 0;

        // Generate in 2 batches of 10 — the model handles 10 reliably
        List<GeneratedQuestion> allQuestions = new ArrayList<>();

        for (int batch = 1; batch <= 2; batch++) {
            log.info("[AssessmentGen] batch {}/2", batch);
            String prompt = buildPrompt(jobTitle, desc, difficulty, batch, years);

            for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
                log.info("[AssessmentGen] batch {} attempt {}/{}", batch, attempt, MAX_RETRIES);
                try {
                    String rawModel = callOllama(prompt);
                    log.info("[AssessmentGen] raw model output (first 800 chars):\n{}",
                            rawModel.length() > 800 ? rawModel.substring(0, 800) + "…" : rawModel);

                    List<GeneratedQuestion> questions = parseQuestions(rawModel);
                    log.info("[AssessmentGen] parsed {} questions from batch {}", questions.size(), batch);

                    if (questions.size() >= MIN_ACCEPTABLE) {
                        allQuestions.addAll(questions);
                        break; // batch succeeded, move to next
                    }
                    log.warn("[AssessmentGen] only {} questions parsed, retrying batch {}", questions.size(), batch);
                } catch (Exception e) {
                    log.warn("[AssessmentGen] batch {} attempt {} exception: {}", batch, attempt, e.getMessage());
                }
            }
        }

        if (allQuestions.size() >= MIN_ACCEPTABLE) {
            log.info("[AssessmentGen] SUCCESS — returning {} AI-generated questions", allQuestions.size());
            return allQuestions;
        }

        // All AI attempts failed — use static fallback so assessment always works
        log.warn("[AssessmentGen] AI failed, got only {} questions. Using fallback bank", allQuestions.size());
        return buildFallback(jobTitle, difficulty);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Prompt — explicit rules + one full example object, PLUS a concrete
    // per-tier description of what "difficulty" should mean in practice.
    // ─────────────────────────────────────────────────────────────────────────

    private String buildPrompt(String jobTitle, String desc, DifficultyLevel difficulty, int batch, int years) {
        String batchHint = batch == 2
                ? " These must be DIFFERENT questions from the previous batch."
                : "";

        String tierGuidance = switch (difficulty) {
            case EASY -> "The candidate has " + years + " year(s) of experience (a fresher/beginner). " +
                    "Ask FUNDAMENTAL questions: basic definitions, core syntax, what things are and " +
                    "what they're used for. Avoid multi-step reasoning, tricky edge cases, or system design.";
            case MEDIUM -> "The candidate has " + years + " year(s) of experience (an intermediate developer). " +
                    "Ask APPLIED questions: when to use one approach over another, common pitfalls, " +
                    "best practices, and how concepts are used together in a real project. " +
                    "Avoid pure definitions — but avoid full system-design or algorithmic proofs too.";
            case HARD -> "The candidate has " + years + " year(s) of experience (a senior/highly skilled engineer). " +
                    "Ask ADVANCED questions: system design trade-offs, performance and scaling concerns, " +
                    "debugging subtle bugs, algorithmic complexity, security implications, and " +
                    "architecture-level decisions. Treat this candidate as an expert — do NOT ask basic " +
                    "definition questions.";
        };

        return String.format(
                "You generate multiple-choice interview questions as JSON.\n\n" +
                        "Task: Create exactly 10 MCQ questions for a \"%s\" role.%s\n" +
                        "Job context: %s\n\n" +
                        "Difficulty calibration:\n%s\n\n" +
                        "Rules:\n" +
                        "- Output ONLY a JSON array. No preamble, no markdown, no explanation.\n" +
                        "- Every element MUST be a JSON OBJECT (never a plain string) with exactly these keys:\n" +
                        "  question, optionA, optionB, optionC, optionD, correctOption\n" +
                        "- correctOption is exactly one of: \"A\", \"B\", \"C\", \"D\"\n\n" +
                        "Example of the REQUIRED format (this is what ONE element must look like):\n" +
                        "{\"question\":\"What does API stand for?\",\"optionA\":\"Application Programming Interface\"," +
                        "\"optionB\":\"Applied Protocol Integration\",\"optionC\":\"Automated Program Interaction\"," +
                        "\"optionD\":\"Application Process Integration\",\"correctOption\":\"A\"}\n\n" +
                        "Now output the full JSON array of 10 such objects, calibrated to the difficulty above:",
                jobTitle, batchHint, desc, tierGuidance
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Ollama /api/generate call  (exchange reads raw bytes — bypasses content-type)
    // ─────────────────────────────────────────────────────────────────────────

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model",  model,
                "prompt", prompt,
                "stream", false,
                // num_predict trimmed from 8192 -> 2048. Small models drift/
                // simplify format on very long generations; 10 MCQ objects
                // comfortably fit in ~2000 tokens and staying shorter keeps
                // the model closer to the example format the whole way through.
                "options", Map.of("temperature", 0, "num_predict", 2048)
        );

        String rawBody;
        try {
            rawBody = restClient.post()
                    .uri("/api/generate")
                    .body(requestBody)
                    .exchange((req, res) -> {
                        try {
                            byte[] bytes = res.getBody().readAllBytes();
                            return new String(bytes, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new AssessmentGenerationException("Read error: " + e.getMessage(), e);
                        }
                    });
        } catch (AssessmentGenerationException age) {
            throw age;
        } catch (Exception e) {
            throw new AssessmentGenerationException("Ollama unreachable: " + e.getMessage(), e);
        }

        if (rawBody == null || rawBody.isBlank()) {
            throw new AssessmentGenerationException("Ollama returned empty body");
        }

        // Unwrap /api/generate envelope: { "response": "<model output>", ... }
        try {
            JsonNode env = objectMapper.readTree(rawBody);
            if (env.has("error")) {
                throw new AssessmentGenerationException("Ollama error: " + env.path("error").asText());
            }
            JsonNode resp = env.path("response");
            if (resp.isMissingNode() || resp.isNull()) {
                throw new AssessmentGenerationException(
                        "Missing 'response' in Ollama envelope. Body snippet: "
                                + rawBody.substring(0, Math.min(300, rawBody.length())));
            }
            return resp.asText();
        } catch (AssessmentGenerationException age) {
            throw age;
        } catch (Exception e) {
            throw new AssessmentGenerationException("Envelope parse error: " + e.getMessage(), e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Parsing — lenient, handles many model output variations
    // ─────────────────────────────────────────────────────────────────────────

    private List<GeneratedQuestion> parseQuestions(String raw) throws Exception {
        String cleaned = extractJsonArray(raw);
        log.info("[AssessmentGen] extracted JSON (first 300 chars): {}",
                cleaned.length() > 300 ? cleaned.substring(0, 300) + "…" : cleaned);

        JsonNode root  = objectMapper.readTree(cleaned);

        // Handle both plain array and { "questions": [...] } wrapper
        JsonNode array = root.isArray() ? root : root.path("questions");
        log.info("[AssessmentGen] array has {} elements", array.size());

        // Fast, clear failure instead of silently skipping every item when the
        // model returns plain strings ["What is...", ...] instead of objects.
        if (array.size() > 0 && !array.get(0).isObject()) {
            log.warn("[AssessmentGen] model returned plain strings instead of JSON objects " +
                            "(e.g. \"{}\") — rejecting batch, will retry",
                    array.get(0).asText());
            return new ArrayList<>();
        }

        List<GeneratedQuestion> result = new ArrayList<>();
        for (int idx = 0; idx < array.size(); idx++) {
            JsonNode node = array.get(idx);
            String q   = nodeText(node, "question", "text", "q");
            if (q.isEmpty()) { log.debug("[AssessmentGen] item {} skipped: no question text", idx); continue; }

            String optA = nodeText(node, "optionA", "option_a", "a");
            String optB = nodeText(node, "optionB", "option_b", "b");
            String optC = nodeText(node, "optionC", "option_c", "c");
            String optD = nodeText(node, "optionD", "option_d", "d");
            String cor  = extractCorrectLetter(node,
                    "correctOption", "correctAnswer", "answer", "correct", "correct_option");

            if (cor == null) { log.debug("[AssessmentGen] item {} skipped: no correctOption", idx); continue; }
            if (optA.isEmpty() || optB.isEmpty()) { log.debug("[AssessmentGen] item {} skipped: missing options", idx); continue; }
            result.add(new GeneratedQuestion(q, optA, optB, optC, optD, cor));
        }
        return result;
    }

    private String nodeText(JsonNode node, String... fields) {
        for (String f : fields) {
            String v = node.path(f).asText("").trim();
            if (!v.isEmpty()) return v;
        }
        return "";
    }

    /** Scans field candidates for first A/B/C/D character — handles "B", "b", "B.", "B)", "Option B". */
    private String extractCorrectLetter(JsonNode node, String... fields) {
        for (String f : fields) {
            String val = node.path(f).asText("").trim().toUpperCase();
            for (char c : val.toCharArray()) {
                if (c == 'A' || c == 'B' || c == 'C' || c == 'D') return String.valueOf(c);
            }
        }
        return null;
    }

    /**
     * Finds the first [...] block in text; auto-closes truncated arrays.
     * FALLS BACK to stitching together standalone {...}{...} objects when
     * the model skips the array wrapper entirely and just emits one JSON
     * object per line (observed with llama3.2 on batch 2 — no '[' anywhere
     * in the output, just objects separated by blank lines).
     */
    private String extractJsonArray(String text) {
        if (text == null || text.isBlank()) return "[]";
        String s = text.trim();
        // Strip markdown fences
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?\\s*", "").trim();
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3).trim();
        }
        int start = s.indexOf('[');
        if (start < 0) {
            // No array bracket anywhere — try to salvage standalone objects.
            return stitchStandaloneObjects(s);
        }

        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc)              { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"')         { inStr = !inStr; continue; }
            if (inStr)            continue;
            if (c == '[') depth++;
            else if (c == ']') { depth--; if (depth == 0) return s.substring(start, i + 1); }
        }
        // Auto-close truncated array
        StringBuilder closed = new StringBuilder(s.substring(start));
        for (int i = 0; i < depth; i++) closed.append(']');
        return closed.toString();
    }

    /**
     * Scans text for top-level {...} objects placed back-to-back (no array
     * wrapper, no commas — just whitespace/newlines between them) and joins
     * them into a single valid JSON array string. Ignores any non-JSON text
     * between objects (preamble, stray commentary, etc).
     */
    private String stitchStandaloneObjects(String s) {
        List<String> objects = new ArrayList<>();
        int i = 0;
        int n = s.length();
        while (i < n) {
            int objStart = s.indexOf('{', i);
            if (objStart < 0) break;

            int depth = 0;
            boolean inStr = false, esc = false;
            int j = objStart;
            for (; j < n; j++) {
                char c = s.charAt(j);
                if (esc)               { esc = false; continue; }
                if (c == '\\' && inStr) { esc = true; continue; }
                if (c == '"')          { inStr = !inStr; continue; }
                if (inStr)             continue;
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) { j++; break; }
                }
            }
            if (depth != 0) {
                // Truncated final object — auto-close and take what we have.
                StringBuilder closed = new StringBuilder(s.substring(objStart, n));
                for (int k = 0; k < depth; k++) closed.append('}');
                objects.add(closed.toString());
                break;
            }
            objects.add(s.substring(objStart, j));
            i = j;
        }

        if (objects.isEmpty()) return "[]";
        return "[" + String.join(",", objects) + "]";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Fallback — 20 generic tech questions used when AI generation fails.
    // Ensures the assessment flow is never blocked by model issues.
    // ─────────────────────────────────────────────────────────────────────────

    private List<GeneratedQuestion> buildFallback(String jobTitle, DifficultyLevel difficulty) {
        log.info("[AssessmentGen] Serving fallback questions (difficulty={})", difficulty);
        return List.of(
                new GeneratedQuestion("What does API stand for?",
                        "Application Programming Interface", "Automated Program Interaction",
                        "Applied Protocol Integration", "Application Process Integration", "A"),
                new GeneratedQuestion("Which HTTP method is used to CREATE a new resource?",
                        "GET", "POST", "DELETE", "PUT", "B"),
                new GeneratedQuestion("What is the primary purpose of version control (e.g. Git)?",
                        "Speed up compilation",
                        "Track and manage code changes over time",
                        "Deploy applications automatically",
                        "Encrypt source code", "B"),
                new GeneratedQuestion("Which best describes a REST API?",
                        "A stateful protocol requiring persistent connections",
                        "A binary messaging protocol",
                        "A stateless architectural style using HTTP methods",
                        "A database query language", "C"),
                new GeneratedQuestion("What does SQL stand for?",
                        "Structured Query Language", "Simple Question Language",
                        "Standard Queue Logic", "System Query Library", "A"),
                new GeneratedQuestion("Which data structure operates on a Last-In-First-Out (LIFO) basis?",
                        "Queue", "Linked List", "Stack", "Tree", "C"),
                new GeneratedQuestion("What is the purpose of a foreign key in a relational database?",
                        "Speed up full-table scans",
                        "Enforce a link between records in two tables",
                        "Encrypt sensitive columns", "Automatically back up data", "B"),
                new GeneratedQuestion("In OOP, what is encapsulation?",
                        "Inheriting behaviour from a parent class",
                        "Overriding a method in a subclass",
                        "Bundling data and methods within a class",
                        "Defining multiple methods with the same name", "C"),
                new GeneratedQuestion("What does CI/CD stand for?",
                        "Code Integration / Code Deployment",
                        "Continuous Integration / Continuous Delivery",
                        "Central Infrastructure / Container Delivery",
                        "Core Interface / Component Design", "B"),
                new GeneratedQuestion("Which of the following is a NoSQL database?",
                        "PostgreSQL", "MySQL", "Oracle DB", "MongoDB", "D"),
                new GeneratedQuestion("What is the time complexity of binary search?",
                        "O(n)", "O(n log n)", "O(log n)", "O(1)", "C"),
                new GeneratedQuestion("Which HTTP status code indicates a resource was not found?",
                        "200", "301", "404", "500", "C"),
                new GeneratedQuestion("What does the term 'idempotent' mean for an HTTP method?",
                        "The request is encrypted",
                        "Multiple identical requests produce the same result as a single one",
                        "The server never stores state",
                        "The response is always cached", "B"),
                new GeneratedQuestion("What is a microservice architecture?",
                        "A single large application handling all functionality",
                        "An app built as a collection of small independent services",
                        "A design for database partitioning",
                        "A caching strategy for web apps", "B"),
                new GeneratedQuestion("Which of the following is a creational design pattern?",
                        "Observer", "Strategy", "Singleton", "Decorator", "C"),
                new GeneratedQuestion("What does ACID stand for in databases?",
                        "Atomicity, Consistency, Isolation, Durability",
                        "Automated, Consistent, Indexed, Distributed",
                        "Asynchronous, Concurrent, Integrated, Dynamic",
                        "Authenticated, Committed, Isolated, Durable", "A"),
                new GeneratedQuestion("Which protocol is used to send emails?",
                        "HTTP", "FTP", "SMTP", "SSH", "C"),
                new GeneratedQuestion("What is the main purpose of Docker?",
                        "Version control for code",
                        "Running virtual machines",
                        "Containerising applications for consistent deployment",
                        "Database management", "C"),
                new GeneratedQuestion("What is a JWT used for?",
                        "Database transactions",
                        "Stateless authentication and information exchange",
                        "Compressing JSON data",
                        "Encrypting database passwords", "B"),
                new GeneratedQuestion("Which sorting algorithm has O(n log n) average-case complexity?",
                        "Bubble Sort", "Insertion Sort", "Merge Sort", "Selection Sort", "C")
        );
    }
}
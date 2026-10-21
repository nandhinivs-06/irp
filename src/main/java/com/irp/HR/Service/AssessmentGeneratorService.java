package com.irp.HR.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Model.DifficultyLevel;
import com.irp.HR.Model.QuestionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Generates 20 assessment questions:
 * - 12 General MCQs
 * - 5 Coding MCQs (with code snippets / algorithm analysis)
 * - 3 Hands-on Coding Problems (with test cases and multi-language starter templates)
 */
@Service
public class AssessmentGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(AssessmentGeneratorService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    private static final int GENERAL_MCQ_COUNT = 12;
    private static final int CODING_MCQ_COUNT  = 5;
    private static final int CODING_PROB_COUNT = 3;
    private static final int MAX_RETRIES       = 2;

    public AssessmentGeneratorService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3.2}") String model
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

    public List<GeneratedQuestion> generateQuestions(
            String jobTitle, String jobDescription, DifficultyLevel difficulty, Integer yearsOfExperience) {

        log.info("[AssessmentGen] Generating assessment for '{}', difficulty={}, exp={} yrs",
                jobTitle, difficulty, yearsOfExperience);

        // 1. Get 12 General MCQs
        List<GeneratedQuestion> generalMcqs = getGeneralMcqs(jobTitle, jobDescription, difficulty, yearsOfExperience);

        // 2. Get 5 Coding MCQs
        List<GeneratedQuestion> codingMcqs = getCodingMcqs(difficulty);

        // 3. Get 3 Hands-on Coding Problems
        List<GeneratedQuestion> codingProblems = getHandsOnCodingQuestions(difficulty);

        List<GeneratedQuestion> all = new ArrayList<>();
        all.addAll(generalMcqs);
        all.addAll(codingMcqs);
        all.addAll(codingProblems);

        log.info("[AssessmentGen] Created total {} questions: {} general MCQs, {} coding MCQs, {} coding problems",
                all.size(), generalMcqs.size(), codingMcqs.size(), codingProblems.size());

        return all;
    }

    private List<GeneratedQuestion> getGeneralMcqs(String jobTitle, String jobDesc, DifficultyLevel difficulty, Integer years) {
        String desc = (jobDesc == null ? "" : jobDesc);
        if (desc.length() > 300) desc = desc.substring(0, 300) + "...";
        int exp = years != null ? years : 0;

        String prompt = buildPrompt(jobTitle, desc, difficulty, exp);
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String rawModel = callOllama(prompt);
                List<GeneratedQuestion> parsed = parseQuestions(rawModel);
                if (parsed.size() >= 8) {
                    if (parsed.size() > GENERAL_MCQ_COUNT) {
                        return new ArrayList<>(parsed.subList(0, GENERAL_MCQ_COUNT));
                    } else if (parsed.size() == GENERAL_MCQ_COUNT) {
                        return parsed;
                    }
                }
            } catch (Exception e) {
                log.warn("[AssessmentGen] Ollama general MCQ generation attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        log.info("[AssessmentGen] Using curated General MCQ bank");
        return getCuratedGeneralMcqs(difficulty);
    }

    private String buildPrompt(String jobTitle, String desc, DifficultyLevel difficulty, int years) {
        return """
                You are a senior technical interviewer creating a multiple-choice assessment for:
                Job Title: %s
                Target Experience: %d years (Difficulty: %s)
                Job Summary: %s
                
                Generate exactly 12 multiple-choice questions (MCQs) testing core concepts, APIs, databases, architecture, and engineering principles.
                
                Respond ONLY with a JSON array of objects:
                [
                  {
                    "question": "What is the primary benefit of connection pooling in databases?",
                    "optionA": "It permanently reserves database memory",
                    "optionB": "It reduces the overhead of repeatedly opening and closing connections",
                    "optionC": "It automatically encrypts query results",
                    "optionD": "It replaces indexing on foreign keys",
                    "correctOption": "B"
                  }
                ]
                """.formatted(jobTitle, years, difficulty, desc);
    }

    private String callOllama(String prompt) {
        Map<String, Object> body = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "options", Map.of("temperature", 0.3)
        );

        String json = restClient.post()
                .uri("/api/generate")
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .body(String.class);

        try {
            JsonNode root = objectMapper.readTree(json);
            return root.path("response").asText("");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Ollama response wrapper: " + e.getMessage(), e);
        }
    }

    private List<GeneratedQuestion> parseQuestions(String raw) {
        List<GeneratedQuestion> result = new ArrayList<>();
        if (raw == null || raw.isBlank()) return result;

        String jsonArray = extractJsonArray(raw);
        JsonNode arr;
        try {
            arr = objectMapper.readTree(jsonArray);
        } catch (Exception e) {
            return result;
        }

        if (!arr.isArray()) return result;

        for (int i = 0; i < arr.size(); i++) {
            JsonNode node = arr.get(i);
            String q = nodeText(node, "question", "text", "q");
            String optA = nodeText(node, "optionA", "option_a", "a");
            String optB = nodeText(node, "optionB", "option_b", "b");
            String optC = nodeText(node, "optionC", "option_c", "c");
            String optD = nodeText(node, "optionD", "option_d", "d");
            String cor = extractCorrectLetter(node, "correctOption", "correctAnswer", "answer", "correct");

            if (!q.isEmpty() && !optA.isEmpty() && !optB.isEmpty() && cor != null) {
                result.add(GeneratedQuestion.mcq(q, optA, optB, optC, optD, cor));
            }
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

    private String extractCorrectLetter(JsonNode node, String... fields) {
        for (String f : fields) {
            String val = node.path(f).asText("").trim().toUpperCase();
            for (char c : val.toCharArray()) {
                if (c == 'A' || c == 'B' || c == 'C' || c == 'D') return String.valueOf(c);
            }
        }
        return null;
    }

    private String extractJsonArray(String text) {
        if (text == null || text.isBlank()) return "[]";
        String s = text.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```(json)?\\s*", "").trim();
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3).trim();
        }
        int start = s.indexOf('[');
        if (start < 0) return "[]";

        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (esc) { esc = false; continue; }
            if (c == '\\' && inStr) { esc = true; continue; }
            if (c == '"') { inStr = !inStr; continue; }
            if (inStr) continue;
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return s.substring(start, i + 1);
            }
        }
        StringBuilder closed = new StringBuilder(s.substring(start));
        for (int i = 0; i < depth; i++) closed.append(']');
        return closed.toString();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5 Coding MCQs (Code snippets, algorithm complexity, output prediction)
    // ─────────────────────────────────────────────────────────────────────────
    private List<GeneratedQuestion> getCodingMcqs(DifficultyLevel difficulty) {
        return List.of(
                GeneratedQuestion.codingMcq(
                        "What is the output of the following JavaScript code?\n\n```javascript\nconsole.log(1 + '2' + 3);\nconsole.log(4 - '2' + 1);\n```",
                        "\"123\" and 3",
                        "6 and 3",
                        "\"123\" and \"421\"",
                        "TypeError and 3",
                        "A"
                ),
                GeneratedQuestion.codingMcq(
                        "What is the time complexity of the following algorithm?\n\n```java\nfor (int i = 0; i < n; i++) {\n    for (int j = 1; j < n; j *= 2) {\n        sum += (i + j);\n    }\n}\n```",
                        "O(n^2)",
                        "O(n log n)",
                        "O(n)",
                        "O(log n)",
                        "B"
                ),
                GeneratedQuestion.codingMcq(
                        "In Python, what is the output of the following function calls?\n\n```python\ndef append_item(x, lst=[]):\n    lst.append(x)\n    return lst\n\nprint(append_item(1))\nprint(append_item(2))\n```",
                        "[1] and [2]",
                        "[1] and [1, 2]",
                        "[1, 2] and [1, 2]",
                        "TypeError: mutable default argument",
                        "B"
                ),
                GeneratedQuestion.codingMcq(
                        "In Java, what will this code print?\n\n```java\nString s1 = \"hello\";\nString s2 = new String(\"hello\");\nSystem.out.println((s1 == s2) + \" \" + s1.equals(s2));\n```",
                        "true true",
                        "false false",
                        "false true",
                        "true false",
                        "C"
                ),
                GeneratedQuestion.codingMcq(
                        "What does the following recursive function compute for positive integer n?\n\n```python\ndef mystery(n):\n    if n <= 1: return 1\n    return n * mystery(n - 1)\n```",
                        "Fibonacci sequence at n",
                        "Sum of first n integers",
                        "Factorial of n (n!)",
                        "2 to the power of n",
                        "C"
                )
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3 Hands-on Coding Problems with Test Cases & Multi-language Boilerplates
    // ─────────────────────────────────────────────────────────────────────────
    private List<GeneratedQuestion> getHandsOnCodingQuestions(DifficultyLevel difficulty) {
        String allowedLangs = "python,javascript,java";

        // Problem 1: Palindrome Check
        String p1Desc = """
                ### Problem 1: Palindrome Verification
                Write a function that checks whether a given string is a **palindrome** (reads the same forwards and backwards).
                - Ignore all non-alphanumeric characters.
                - Comparison must be case-insensitive.
                
                **Input Format:**
                A single line from standard input containing the string.
                
                **Output Format:**
                Print `true` if it is a palindrome, or `false` otherwise.
                """;

        String p1Starters = """
                {
                  "python": "import sys\\n\\ndef is_palindrome(s):\\n    # TODO: Write your solution here\\n    pass\\n\\nif __name__ == '__main__':\\n    line = sys.stdin.read().strip()\\n    print(is_palindrome(line))\\n",
                  "javascript": "const fs = require('fs');\\n\\nfunction isPalindrome(s) {\\n    // TODO: Write your solution here\\n    return '';\\n}\\n\\nconst input = fs.readFileSync(0, 'utf-8').trim();\\nconsole.log(isPalindrome(input));\\n",
                  "java": "import java.io.*;\\nimport java.util.*;\\n\\npublic class Solution {\\n    public static void main(String[] args) throws Exception {\\n        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\\n        String s = br.readLine();\\n        if (s == null) s = \\"\\";\\n        // TODO: Write your solution here\\n        System.out.println(\\"\\");\\n    }\\n}\\n"
                }
                """;

        String p1TestCases = """
                [
                  {"input": "racecar", "expectedOutput": "true", "isHidden": false},
                  {"input": "hello world", "expectedOutput": "false", "isHidden": false},
                  {"input": "A man, a plan, a canal: Panama", "expectedOutput": "true", "isHidden": true}
                ]
                """;

        // Problem 2: Two Sum
        String p2Desc = """
                ### Problem 2: Two Sum
                Given a target sum on the first line and space-separated integers on the second line, find two distinct 0-based indices whose elements sum up to the target.
                
                **Input Format:**
                Line 1: An integer target.
                Line 2: Space-separated integers.
                
                **Output Format:**
                Print the two indices separated by a space (smaller index first, e.g. `0 1`). If no such pair exists, print `-1`.
                """;

        String p2Starters = """
                {
                  "python": "import sys\\n\\ndef two_sum(target, nums):\\n    # TODO: Write your solution here\\n    pass\\n\\nif __name__ == '__main__':\\n    lines = sys.stdin.read().strip().splitlines()\\n    if len(lines) >= 2:\\n        t = int(lines[0].strip())\\n        arr = list(map(int, lines[1].strip().split()))\\n        print(two_sum(t, arr))\\n    else:\\n        print('-1')\\n",
                  "javascript": "const fs = require('fs');\\n\\nfunction twoSum(target, nums) {\\n    // TODO: Write your solution here\\n    return '';\\n}\\n\\nconst lines = fs.readFileSync(0, 'utf-8').trim().split('\\n');\\nif (lines.length >= 2) {\\n    const target = parseInt(lines[0].trim());\\n    const nums = lines[1].trim().split(/\\\\s+/).map(Number);\\n    console.log(twoSum(target, nums));\\n} else {\\n    console.log('');\\n}\\n",
                  "java": "import java.io.*;\\nimport java.util.*;\\n\\npublic class Solution {\\n    public static void main(String[] args) throws Exception {\\n        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\\n        String line1 = br.readLine();\\n        String line2 = br.readLine();\\n        if (line1 == null || line2 == null) { System.out.println(\\"\\"); return; }\\n        int target = Integer.parseInt(line1.trim());\\n        String[] parts = line2.trim().split(\\"\\\\\\\\s+\\");\\n        // TODO: Write your solution here\\n        System.out.println(\\"\\");\\n    }\\n}\\n"
                }
                """;

        String p2TestCases = """
                [
                  {"input": "9\\n2 7 11 15", "expectedOutput": "0 1", "isHidden": false},
                  {"input": "6\\n3 2 4", "expectedOutput": "1 2", "isHidden": false},
                  {"input": "10\\n1 2 3 4", "expectedOutput": "-1", "isHidden": true}
                ]
                """;

        // Problem 3: Valid Parentheses
        String p3Desc = """
                ### Problem 3: Valid Parentheses
                Given a string containing just the characters `'('`, `')'`, `'{'`, `'}'`, `'['` and `']'`, determine if the input string has valid matching brackets.
                - Open brackets must be closed by the same type of brackets.
                - Open brackets must be closed in the correct order.
                
                **Input Format:**
                A single line containing the bracket string.
                
                **Output Format:**
                Print `true` if valid, or `false` otherwise.
                """;

        String p3Starters = """
                {
                  "python": "import sys\\n\\ndef is_valid(s):\\n    # TODO: Write your solution here\\n    pass\\n\\nif __name__ == '__main__':\\n    line = sys.stdin.read().strip()\\n    print(is_valid(line))\\n",
                  "javascript": "const fs = require('fs');\\n\\nfunction isValid(s) {\\n    // TODO: Write your solution here\\n    return '';\\n}\\n\\nconst input = fs.readFileSync(0, 'utf-8').trim();\\nconsole.log(isValid(input));\\n",
                  "java": "import java.io.*;\\nimport java.util.*;\\n\\npublic class Solution {\\n    public static void main(String[] args) throws Exception {\\n        BufferedReader br = new BufferedReader(new InputStreamReader(System.in));\\n        String s = br.readLine();\\n        if (s == null) s = \\"\\";\\n        // TODO: Write your solution here\\n        System.out.println(\\"\\");\\n    }\\n}\\n"
                }
                """;

        String p3TestCases = """
                [
                  {"input": "()[]{}", "expectedOutput": "true", "isHidden": false},
                  {"input": "(]", "expectedOutput": "false", "isHidden": false},
                  {"input": "{[]}", "expectedOutput": "true", "isHidden": true}
                ]
                """;

        return List.of(
                GeneratedQuestion.coding(p1Desc.trim(), p1Starters.trim(), p1TestCases.trim(), allowedLangs),
                GeneratedQuestion.coding(p2Desc.trim(), p2Starters.trim(), p2TestCases.trim(), allowedLangs),
                GeneratedQuestion.coding(p3Desc.trim(), p3Starters.trim(), p3TestCases.trim(), allowedLangs)
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Curated General MCQs (12 questions)
    // ─────────────────────────────────────────────────────────────────────────
    private List<GeneratedQuestion> getCuratedGeneralMcqs(DifficultyLevel difficulty) {
        return List.of(
                GeneratedQuestion.mcq("What does API stand for?",
                        "Application Programming Interface", "Automated Program Interaction",
                        "Applied Protocol Integration", "Application Process Integration", "A"),
                GeneratedQuestion.mcq("Which HTTP method is idempotent and used to update or replace an entire resource?",
                        "POST", "PUT", "PATCH", "DELETE", "B"),
                GeneratedQuestion.mcq("What is the primary benefit of database indexing?",
                        "It eliminates NULL values",
                        "It accelerates data retrieval at the cost of additional write overhead and storage",
                        "It encrypts sensitive tables automatically",
                        "It provides automated database backups", "B"),
                GeneratedQuestion.mcq("Which best describes a RESTful API architecture?",
                        "Stateful sessions stored on server memory",
                        "Stateless client-server communication using standard HTTP methods",
                        "A peer-to-peer binary socket protocol",
                        "An asynchronous file transport mechanism", "B"),
                GeneratedQuestion.mcq("Which data structure operates on a First-In-First-Out (FIFO) basis?",
                        "Stack", "Binary Tree", "Queue", "Hash Map", "C"),
                GeneratedQuestion.mcq("What does ACID stand for in transaction processing?",
                        "Atomicity, Consistency, Isolation, Durability",
                        "Automated, Consistent, Indexed, Distributed",
                        "Asynchronous, Concurrent, Integrated, Dynamic",
                        "Authenticated, Committed, Isolated, Durable", "A"),
                GeneratedQuestion.mcq("In Git, what does `git merge` do compared to `git rebase`?",
                        "Merge deletes the source branch, rebase preserves it",
                        "Merge creates a commit uniting histories, while rebase replays commits on top of another base",
                        "Rebase only works on remote repositories",
                        "Merge cannot resolve merge conflicts", "B"),
                GeneratedQuestion.mcq("What is the average time complexity of searching an element in a balanced Hash Map?",
                        "O(1)", "O(log n)", "O(n)", "O(n log n)", "A"),
                GeneratedQuestion.mcq("Which HTTP status code signifies that the client is not authenticated?",
                        "400 Bad Request", "401 Unauthorized", "403 Forbidden", "404 Not Found", "B"),
                GeneratedQuestion.mcq("What is the primary role of a connection pool like HikariCP?",
                        "To compile SQL statements into native machine code",
                        "To reuse physical database connections to minimize latency and overhead",
                        "To shard database tables across multiple servers",
                        "To cache HTTP responses on the client side", "B"),
                GeneratedQuestion.mcq("What is the main advantage of containerization with Docker?",
                        "It provides virtual hardware emulation like full hypervisors",
                        "It packages an application and its dependencies into a lightweight, consistent environment",
                        "It guarantees zero CPU usage when idle",
                        "It replaces relational database management systems", "B"),
                GeneratedQuestion.mcq("In microservices, what is the purpose of an API Gateway?",
                        "It serves as a single entry point for routing, authentication, and rate limiting",
                        "It replaces all microservice databases with a single shared schema",
                        "It converts all HTTP requests directly into hardware instructions",
                        "It prevents clients from using HTTPS encryption", "A")
        );
    }
}
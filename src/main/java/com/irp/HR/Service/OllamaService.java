package com.irp.HR.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Dto.ParsedResumeData;
import com.irp.HR.Exception.ResumeParsingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Map;

/**
 * Talks to a locally running Ollama instance to extract skills from raw resume text.
 *
 * Reads the Ollama response as a raw String first (avoids content-type mismatches
 * where Ollama returns application/octet-stream instead of application/json),
 * then parses manually with Jackson.
 */
@Service
public class OllamaService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    public OllamaService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3}") String model
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10_000);   // 10s to establish connection
        factory.setReadTimeout(180_000);     // 3 min — local model generation is slow
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.model = model;
    }

    public ParsedResumeData extractSkills(String resumeText) {
        String prompt = """
                You are a resume parser. Read the resume text below and extract:
                1. A list of technical and professional skills mentioned in it
                   (programming languages, frameworks, tools, methodologies).
                2. The candidate's total years of professional work experience,
                   as a whole number. Estimate from listed job durations /
                   graduation date if not stated explicitly. If the resume gives
                   genuinely no signal (e.g. it is blank or unreadable), use 0.

                Respond with ONLY a raw JSON object, nothing else - no markdown,
                no explanation, matching exactly this shape:
                {
                  "skills": ["Java", "Spring Boot", "PostgreSQL", "REST APIs"],
                  "yearsOfExperience": 3
                }

                Resume text:
                %s
                """.formatted(resumeText);

        Map<String, Object> options = Map.of(
                "temperature", 0,     // deterministic output
                "num_predict", 512    // enough tokens for the JSON reply
        );

        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "options", options
        );

        // ── Step 1: call Ollama, read response as raw bytes ───────────────────
        // We MUST use exchange() here. Ollama returns Content-Type:
        // application/octet-stream, which Spring's StringHttpMessageConverter
        // and the typed ParameterizedTypeReference converters both reject.
        // exchange() gives us direct access to the raw InputStream, bypassing
        // all message converters entirely.
        String rawResponseBody;
        try {
            rawResponseBody = restClient.post()
                    .uri("/api/generate")
                    .body(requestBody)
                    .exchange((req, res) -> {
                        try {
                            byte[] bytes = res.getBody().readAllBytes();
                            return new String(bytes, StandardCharsets.UTF_8);
                        } catch (IOException e) {
                            throw new ResumeParsingException(
                                    "Failed to read Ollama response body: " + e.getMessage());
                        }
                    });
        } catch (ResumeParsingException rpe) {
            throw rpe;
        } catch (Exception e) {
            throw new ResumeParsingException(
                    "Could not reach Ollama at the configured base-url. Is it running? (" + e.getMessage() + ")"
            );
        }

        if (rawResponseBody == null || rawResponseBody.isBlank()) {
            throw new ResumeParsingException("Ollama returned an empty response while parsing the resume.");
        }

        // ── Step 2: parse the outer Ollama envelope ───────────────────────────
        String innerJson;
        try {
            JsonNode envelope = objectMapper.readTree(rawResponseBody);
            JsonNode responseNode = envelope.path("response");
            if (responseNode.isMissingNode() || responseNode.isNull()) {
                throw new ResumeParsingException("Ollama envelope missing 'response' field. Got: " + rawResponseBody);
            }
            innerJson = responseNode.asText();
        } catch (ResumeParsingException rpe) {
            throw rpe;
        } catch (Exception e) {
            throw new ResumeParsingException(
                    "Failed to parse Ollama envelope JSON. Got: " + rawResponseBody
            );
        }

        // ── Step 3: extract the JSON object from the inner response ───────────
        String cleaned = extractJsonObject(innerJson);

        // ── Step 4: map to ParsedResumeData ───────────────────────────────────
        try {
            JsonNode root = objectMapper.readTree(cleaned);
            List<String> skills = objectMapper.convertValue(
                    root.path("skills"), new TypeReference<List<String>>() {});
            Integer yearsOfExperience = root.path("yearsOfExperience").isInt()
                    ? root.path("yearsOfExperience").asInt()
                    : null;
            return new ParsedResumeData(skills, yearsOfExperience);
        } catch (Exception e) {
            throw new ResumeParsingException(
                    "Ollama's response wasn't valid JSON in the expected shape. Got: " + cleaned
            );
        }
    }

    /**
     * Robustly extracts the first complete JSON object {...} from Ollama's inner
     * response string, tolerating:
     *  - markdown code fences (```json ... ```)
     *  - preamble/postamble text before or after the JSON
     *  - truncated output (auto-closes missing braces)
     */
    private String extractJsonObject(String text) {
        if (text == null || text.isBlank()) return "{}";

        // 1. Strip code fences (``` or ```json)
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?\\s*", "").trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }

        // 2. Locate first '{' and find its matching '}'
        int start = cleaned.indexOf('{');
        if (start < 0) return cleaned; // no JSON object found — let caller fail

        int depth = 0;
        boolean inString = false;
        boolean escape = false;

        for (int i = start; i < cleaned.length(); i++) {
            char c = cleaned.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return cleaned.substring(start, i + 1);
            }
        }

        // 3. Truncated JSON — append the missing closing braces
        StringBuilder closed = new StringBuilder(cleaned.substring(start));
        for (int i = 0; i < depth; i++) closed.append('}');
        return closed.toString();
    }
}
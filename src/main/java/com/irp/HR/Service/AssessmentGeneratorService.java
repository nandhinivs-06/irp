package com.irp.HR.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Exception.AssessmentGenerationException;
import com.irp.HR.Model.DifficultyLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Calls the local Ollama instance to generate multiple-choice questions from
 * a job description at a given difficulty level. Mirrors OllamaService's
 * RestClient setup so both hit the same local Ollama install.
 */
@Service
public class AssessmentGeneratorService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final String model;

    private static final int REQUIRED_QUESTION_COUNT = 20;
    private static final int MAX_RETRIES = 2;

    public AssessmentGeneratorService(
            @Value("${ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${ollama.model:llama3}") String model
    ) {
        this.restClient = RestClient.create(baseUrl);
        this.model = model;
    }

    public List<GeneratedQuestion> generateQuestions(String jobTitle, String jobDescription, DifficultyLevel difficulty) {
        String prompt = buildPrompt(jobTitle, jobDescription, difficulty, REQUIRED_QUESTION_COUNT);

        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            try {
                String rawResponse = callOllama(prompt);
                List<GeneratedQuestion> questions = parseQuestions(rawResponse);
                if (questions.size() == REQUIRED_QUESTION_COUNT) {
                    return questions;
                }
                lastError = new AssessmentGenerationException(
                        "Model returned " + questions.size() + " questions, expected " + REQUIRED_QUESTION_COUNT);
            } catch (Exception e) {
                lastError = new AssessmentGenerationException("Failed to generate assessment questions", e);
            }
        }
        throw lastError;
    }

    private String buildPrompt(String jobTitle, String jobDescription, DifficultyLevel difficulty, int count) {
        return """
                You are an expert technical interviewer. Generate exactly %d multiple-choice
                questions to assess a candidate applying for the role below.

                Job title: %s
                Job description:
                %s

                Difficulty level: %s
                Rules:
                - Questions must test practical, job-relevant knowledge implied by the description above.
                - Exactly 4 options per question, labeled A, B, C, D.
                - Exactly one correct option per question.
                - Do not include explanations.
                - Respond with ONLY a raw JSON array, nothing else - no markdown, no
                  commentary, matching exactly this shape:
                [
                  {
                    "question": "string",
                    "optionA": "string",
                    "optionB": "string",
                    "optionC": "string",
                    "optionD": "string",
                    "correctOption": "A"
                  }
                ]
                """.formatted(count, jobTitle, jobDescription, difficulty.name());
    }

    private String callOllama(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false
        );

        Map<String, Object> response;
        try {
            response = restClient.post()
                    .uri("/api/generate")
                    .body(requestBody)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new AssessmentGenerationException(
                    "Could not reach Ollama at the configured base-url. Is it running? (" + e.getMessage() + ")", e);
        }

        if (response == null || response.get("response") == null) {
            throw new AssessmentGenerationException("Ollama returned an empty response while generating questions.");
        }
        return response.get("response").toString();
    }

    private List<GeneratedQuestion> parseQuestions(String rawResponse) throws Exception {
        String cleaned = stripCodeFences(rawResponse);

        JsonNode array = objectMapper.readTree(cleaned);
        List<GeneratedQuestion> result = new ArrayList<>();

        for (JsonNode node : array) {
            String correct = node.path("correctOption").asText("").trim().toUpperCase();
            if (!correct.matches("[ABCD]")) {
                continue; // skip malformed entries rather than fail the whole batch
            }
            result.add(new GeneratedQuestion(
                    node.path("question").asText(""),
                    node.path("optionA").asText(""),
                    node.path("optionB").asText(""),
                    node.path("optionC").asText(""),
                    node.path("optionD").asText(""),
                    correct
            ));
        }
        return result;
    }

    /** Same fence-stripping behavior as OllamaService, kept local to avoid a cross-dependency. */
    private String stripCodeFences(String text) {
        String cleaned = text.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(json)?", "").trim();
            if (cleaned.endsWith("```")) {
                cleaned = cleaned.substring(0, cleaned.length() - 3).trim();
            }
        }
        return cleaned;
    }
}

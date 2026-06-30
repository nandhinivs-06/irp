package com.irp.HR.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Dto.ParsedResumeData;
import com.irp.HR.Exception.ResumeParsingException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Talks to a locally running Ollama instance to extract skills from raw
 * resume text.
 *
 * Assumes Ollama is running at ollama.base-url (default
 * http://localhost:11434) with the model named in ollama.model already
 * pulled, e.g.:
 *   ollama pull llama3
 *
 * Both are configurable in application.properties:
 *   ollama.base-url=http://localhost:11434
 *   ollama.model=llama3
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
        this.restClient = RestClient.create(baseUrl);
        this.model = model;
    }

    public ParsedResumeData extractSkills(String resumeText) {
        String prompt = """
                You are a resume parser. Read the resume text below and extract
                a list of technical and professional skills mentioned in it
                (programming languages, frameworks, tools, methodologies).

                Respond with ONLY a JSON array of strings, nothing else - no
                markdown, no explanation.
                Example: ["Java", "Spring Boot", "PostgreSQL", "REST APIs"]

                Resume text:
                %s
                """.formatted(resumeText);

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
            throw new ResumeParsingException(
                    "Could not reach Ollama at the configured base-url. Is it running? (" + e.getMessage() + ")"
            );
        }

        if (response == null || response.get("response") == null) {
            throw new ResumeParsingException("Ollama returned an empty response while parsing the resume.");
        }

        String rawSkillsJson = stripCodeFences(response.get("response").toString());

        try {
            List<String> skills = objectMapper.readValue(rawSkillsJson, new TypeReference<List<String>>() {});
            return new ParsedResumeData(skills);
        } catch (Exception e) {
            throw new ResumeParsingException(
                    "Ollama's response wasn't a valid JSON array of skills. Got: " + rawSkillsJson
            );
        }
    }

    /** Some models wrap JSON in ```json ... ``` despite being told not to - strip it if present. */
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

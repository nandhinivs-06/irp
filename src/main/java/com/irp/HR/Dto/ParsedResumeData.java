package com.irp.HR.Dto;

import java.util.List;

/**
 * Structured result from OllamaService after parsing a resume's raw text.
 */
public record ParsedResumeData(
        List<String> skills,
        Integer yearsOfExperience // best-effort estimate from the model; null if it couldn't tell
) {
}
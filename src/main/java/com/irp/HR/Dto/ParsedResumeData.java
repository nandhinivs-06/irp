package com.irp.HR.Dto;

import java.util.List;

/**
 * Structured result from OllamaService after parsing a resume's raw text.
 * Kept to just skills for now since that's all the matching logic needs -
 * extend with education/experience later if another module needs them.
 */
public record ParsedResumeData(
        List<String> skills
) {
}
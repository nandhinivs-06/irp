package com.irp.HR.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * What the recruiter submits to create a job: title, the pasted JD text,
 * and now a separate box for required skills (comma-separated — e.g.
 * "Java, Spring Boot, PostgreSQL"). Kept as one free-text field rather
 * than a list, since the form has one box for it.
 */
public record JobRequest(

        @NotBlank(message = "Job title is required")
        @Size(min = 3, max = 255, message = "Title must be between 3 and 255 characters")
        String title,

        @NotBlank(message = "Job description is required")
        @Size(min = 20, message = "Paste the full job description (min 20 characters)")
        String description,

        @NotBlank(message = "Required skills are required")
        @Size(min = 2, message = "List at least one required skill")
        String requiredSkills
) {
}
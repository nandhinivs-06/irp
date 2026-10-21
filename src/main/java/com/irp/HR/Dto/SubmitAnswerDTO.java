package com.irp.HR.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

@Data
public class SubmitAnswerDTO {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    // For MCQs: "A" | "B" | "C" | "D". Null for coding questions.
    @Pattern(regexp = "^[ABCD]?$", message = "selectedOption must be one of A, B, C, D")
    private String selectedOption;

    // For coding questions
    private String submittedCode;

    // "python" | "javascript" | "java"
    private String selectedLanguage;
}

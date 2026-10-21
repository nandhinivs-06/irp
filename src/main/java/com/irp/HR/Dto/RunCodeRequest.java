package com.irp.HR.Dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class RunCodeRequest {

    @NotNull(message = "questionId is required")
    private UUID questionId;

    @NotBlank(message = "code is required")
    private String code;

    @NotBlank(message = "language is required")
    private String language; // "python", "javascript", "java"
}

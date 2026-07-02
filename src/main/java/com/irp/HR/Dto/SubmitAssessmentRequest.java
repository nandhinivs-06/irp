package com.irp.HR.Dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class SubmitAssessmentRequest {

    @NotNull(message = "assessmentId is required")
    private UUID assessmentId;

    @NotEmpty(message = "answers cannot be empty")
    @Valid
    private List<SubmitAnswerDTO> answers;

    // Reported by the frontend anti-cheat tracker (visibilitychange / blur events)
    private Integer tabSwitchCount;
}

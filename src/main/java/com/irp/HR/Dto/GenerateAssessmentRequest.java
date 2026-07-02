package com.irp.HR.Dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class GenerateAssessmentRequest {

    @NotNull(message = "jobId is required")
    private UUID jobId;

    // candidateId is intentionally NOT part of this DTO.
    // It's derived from the authenticated JWT principal (UserPrincipal) in the
    // controller, never trusted from the request body.
}

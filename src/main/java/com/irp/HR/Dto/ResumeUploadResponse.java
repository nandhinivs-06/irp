package com.irp.HR.Dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record ResumeUploadResponse(
        UUID resumeId,
        String fileName,
        String extractedSkills,
        OffsetDateTime uploadedAt
) {
}

package com.irp.HR.Dto;

import java.time.OffsetDateTime;
import java.util.UUID;

public record MatchReportResponse(
        UUID id,
        UUID resumeId,
        UUID jobId,
        double matchScore,
        String matchedSkills,
        String missingSkills,
        OffsetDateTime createdAt
) {
}

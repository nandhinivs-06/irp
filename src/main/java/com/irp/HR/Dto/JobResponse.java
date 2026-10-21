package com.irp.HR.Dto;

import com.irp.HR.Model.Job;
import com.irp.HR.Model.JobStatus;

import java.time.OffsetDateTime;
import java.util.UUID;

public record JobResponse(
        UUID id,
        String title,
        String description,
        String requiredSkills,
        JobStatus status,
        OffsetDateTime createdAt
) {
    public static JobResponse from(Job job) {
        return new JobResponse(
                job.getId(),
                job.getTitle(),
                job.getDescription(),
                job.getRequiredSkills(),
                job.getStatus(),
                job.getCreatedAt()
        );
    }
}
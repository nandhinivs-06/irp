package com.irp.HR.Dto;

import com.irp.HR.Model.AssessmentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentResultDTO {
    private UUID assessmentId;
    private Integer totalQuestions;
    private Integer correctCount;
    private Integer score; // percentage 0-100
    private AssessmentStatus status;
    private LocalDateTime submittedAt;
    private Integer tabSwitchCount;
    private Boolean flaggedForCheating;
}

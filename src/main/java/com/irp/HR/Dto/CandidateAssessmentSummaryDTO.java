package com.irp.HR.Dto;

import com.irp.HR.Model.AssessmentStatus;
import com.irp.HR.Model.DifficultyLevel;
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
public class CandidateAssessmentSummaryDTO {
    private UUID assessmentId;
    private UUID candidateId;
    private String candidateName;
    private String candidateEmail;
    private DifficultyLevel difficultyLevel;
    private AssessmentStatus status;
    private Integer totalQuestions;
    private Integer correctCount;
    private Integer score;
    private Integer tabSwitchCount;
    private Boolean flaggedForCheating;
    private LocalDateTime createdAt;
    private LocalDateTime submittedAt;
}

package com.irp.HR.Dto;

import com.irp.HR.Model.AssessmentStatus;
import com.irp.HR.Model.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentResponseDTO {
    private UUID assessmentId;
    private UUID jobId;
    private DifficultyLevel difficultyLevel;
    private AssessmentStatus status;
    private Integer totalQuestions;
    private LocalDateTime expiresAt;
    private List<QuestionDTO> questions;
}

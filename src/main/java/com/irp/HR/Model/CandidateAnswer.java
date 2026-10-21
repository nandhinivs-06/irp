package com.irp.HR.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "candidate_answers", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"assessment_id", "question_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateAnswer {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private AssessmentQuestion question;

    // For MCQ questions: "A" | "B" | "C" | "D". Null for coding questions.
    @Column(nullable = true, length = 1)
    private String selectedOption;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String submittedCode;

    @Column(nullable = true)
    private String selectedLanguage;

    @Column(nullable = true)
    private Integer passedTestCases;

    @Column(nullable = true)
    private Integer totalTestCases;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String executionOutput;

    @Column(nullable = false)
    private Boolean correct;
}

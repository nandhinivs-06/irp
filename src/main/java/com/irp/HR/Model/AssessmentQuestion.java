package com.irp.HR.Model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Entity
@Table(name = "assessment_questions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentQuestion {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id", nullable = false)
    private Assessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private QuestionType questionType = QuestionType.MCQ;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String questionText;

    @Column(nullable = true)
    private String optionA;

    @Column(nullable = true)
    private String optionB;

    @Column(nullable = true)
    private String optionC;

    @Column(nullable = true)
    private String optionD;

    // For MCQs: "A" | "B" | "C" | "D". Null for coding questions.
    @Column(nullable = true, length = 1)
    private String correctOption;

    @Column(columnDefinition = "TEXT", nullable = true)
    private String starterCode;

    // JSON array of test cases: [{"input": "...", "expectedOutput": "...", "hidden": false}]
    @Column(columnDefinition = "TEXT", nullable = true)
    private String testCases;

    @Column(nullable = true)
    private String allowedLanguages;

    @Column(nullable = false)
    private Integer orderIndex;
}

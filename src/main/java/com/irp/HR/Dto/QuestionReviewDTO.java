package com.irp.HR.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Only ever built and returned for a COMPLETED assessment - correctOption
// and selectedOption are deliberately absent from QuestionDTO (the pre-submit
// version of a question) and only appear here.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionReviewDTO {
    private UUID id;
    private String questionType;
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String correctOption;
    private String selectedOption;
    private String submittedCode;
    private String selectedLanguage;
    private Integer passedTestCases;
    private Integer totalTestCases;
    private String executionOutput;
    private Boolean isCorrect;
    private Integer orderIndex;
}

package com.irp.HR.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

// Safe to send to the frontend. No correctOption field on purpose.
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionDTO {
    private UUID id;
    private String questionType; // "MCQ" | "CODING_MCQ" | "CODING"
    private String questionText;
    private String optionA;
    private String optionB;
    private String optionC;
    private String optionD;
    private String starterCode;
    private String allowedLanguages;
    private String sampleTestCases; // JSON string of visible test cases: [{"input":"..","expectedOutput":".."}]
    private Integer orderIndex;
}

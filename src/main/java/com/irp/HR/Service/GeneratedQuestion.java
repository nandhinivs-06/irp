package com.irp.HR.Service;

import com.irp.HR.Model.QuestionType;

// Internal, server-side only. Never serialized directly to the frontend.
public record GeneratedQuestion(
        QuestionType questionType,
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption, // "A" | "B" | "C" | "D" for MCQs
        String starterCode,   // JSON or template code for coding questions
        String testCases,     // JSON array of test cases
        String allowedLanguages // e.g. "Java, Python, JavaScript"
) {
    public static GeneratedQuestion mcq(String questionText, String optionA, String optionB, String optionC, String optionD, String correctOption) {
        return new GeneratedQuestion(QuestionType.MCQ, questionText, optionA, optionB, optionC, optionD, correctOption, null, null, null);
    }

    public static GeneratedQuestion codingMcq(String questionText, String optionA, String optionB, String optionC, String optionD, String correctOption) {
        return new GeneratedQuestion(QuestionType.CODING_MCQ, questionText, optionA, optionB, optionC, optionD, correctOption, null, null, null);
    }

    public static GeneratedQuestion coding(String questionText, String starterCode, String testCases, String allowedLanguages) {
        return new GeneratedQuestion(QuestionType.CODING, questionText, null, null, null, null, null, starterCode, testCases, allowedLanguages);
    }
}

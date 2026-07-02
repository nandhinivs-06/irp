package com.irp.HR.Service;

// Internal, server-side only. Never serialized to the frontend.
public record GeneratedQuestion(
        String questionText,
        String optionA,
        String optionB,
        String optionC,
        String optionD,
        String correctOption // "A" | "B" | "C" | "D"
) {
}

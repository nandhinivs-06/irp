package com.irp.HR.Exception;

public class AssessmentGenerationException extends RuntimeException {
    public AssessmentGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public AssessmentGenerationException(String message) {
        super(message);
    }
}

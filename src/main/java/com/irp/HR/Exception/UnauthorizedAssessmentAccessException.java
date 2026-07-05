package com.irp.HR.Exception;

public class UnauthorizedAssessmentAccessException extends RuntimeException {
    public UnauthorizedAssessmentAccessException() {
        super("You are not authorized to access this assessment");
    }
}

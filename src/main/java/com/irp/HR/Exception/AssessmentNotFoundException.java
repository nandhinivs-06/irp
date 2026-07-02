package com.irp.HR.Exception;

import java.util.UUID;

public class AssessmentNotFoundException extends RuntimeException {
    public AssessmentNotFoundException(UUID id) {
        super("Assessment not found with id: " + id);
    }
}

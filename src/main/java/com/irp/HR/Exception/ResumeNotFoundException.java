package com.irp.HR.Exception;

import java.util.UUID;

public class ResumeNotFoundException extends RuntimeException {
    public ResumeNotFoundException(UUID resumeId) {
        super("Resume not found: " + resumeId);
    }
}

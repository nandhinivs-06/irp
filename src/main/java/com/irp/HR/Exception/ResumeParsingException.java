package com.irp.HR.Exception;

/**
 * Covers both file-level problems (wrong type, too big, unreadable) and
 * Ollama-side problems (unreachable, bad/non-JSON response). Split into
 * more specific exceptions later if you want different HTTP statuses for
 * "your file is bad" vs "our AI service is down" - right now both return
 * 400, which is a reasonable default for a candidate-facing error either way.
 */
public class ResumeParsingException extends RuntimeException {
    public ResumeParsingException(String message) {
        super(message);
    }
}

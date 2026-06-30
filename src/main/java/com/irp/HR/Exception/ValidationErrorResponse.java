package com.irp.HR.Exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Returned when @Valid fails. Shape:
 * { "message": "Validation failed", "fieldErrors": { "title": "..." }, "status": 400, "timestamp": "..." }
 * Matches the Postman test assertions in HR_Job.postman_collection.json.
 */
@Getter
@Setter
@AllArgsConstructor
@Builder
public class ValidationErrorResponse {
    private int status;
    private String message;
    private Map<String, String> fieldErrors;
    private LocalDateTime timestamp;
}

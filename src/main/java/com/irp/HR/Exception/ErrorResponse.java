package com.irp.HR.Exception;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class ErrorResponse {
    private int status;
    private String message;
    private LocalDateTime timestamp;
}

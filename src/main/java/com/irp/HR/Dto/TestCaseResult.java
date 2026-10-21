package com.irp.HR.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TestCaseResult {
    private int testCaseNumber;
    private String input;
    private String expectedOutput;
    private String actualOutput;
    private boolean passed;
    private String error;
    private long executionTimeMs;
    private boolean isHidden;
}

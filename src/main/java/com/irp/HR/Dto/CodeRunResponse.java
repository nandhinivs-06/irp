package com.irp.HR.Dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeRunResponse {
    private boolean success;
    private int passedCount;
    private int totalCount;
    private List<TestCaseResult> results;
    private String compileError;
}

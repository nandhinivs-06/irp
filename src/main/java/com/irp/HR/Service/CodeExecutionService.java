package com.irp.HR.Service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Dto.CodeRunResponse;
import com.irp.HR.Dto.TestCaseResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class CodeExecutionService {

    private static final Logger log = LoggerFactory.getLogger(CodeExecutionService.class);
    private static final long TIMEOUT_SECONDS = 5;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public static class TestCaseDefinition {
        public String input;
        public String expectedOutput;
        public boolean isHidden;
    }

    public List<TestCaseDefinition> parseTestCases(String testCasesJson) {
        if (testCasesJson == null || testCasesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(testCasesJson, new TypeReference<List<TestCaseDefinition>>() {});
        } catch (Exception e) {
            log.error("Failed to parse test cases json: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    public CodeRunResponse execute(String code, String language, List<TestCaseDefinition> testCases, boolean onlyVisible) {
        List<TestCaseResult> results = new ArrayList<>();
        int passedCount = 0;
        int testCaseIndex = 1;

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("irp_code_exec_");
            String lang = language != null ? language.trim().toLowerCase() : "python";

            for (TestCaseDefinition tc : testCases) {
                if (onlyVisible && tc.isHidden) {
                    continue;
                }

                TestCaseResult result = runSingleTestCase(tempDir, code, lang, tc.input, tc.expectedOutput, testCaseIndex, tc.isHidden);
                if (result.isPassed()) {
                    passedCount++;
                }
                results.add(result);
                testCaseIndex++;
            }

            return CodeRunResponse.builder()
                    .success(true)
                    .passedCount(passedCount)
                    .totalCount(results.size())
                    .results(results)
                    .build();

        } catch (Exception e) {
            log.error("Error executing code: {}", e.getMessage(), e);
            return CodeRunResponse.builder()
                    .success(false)
                    .compileError("Execution failed: " + e.getMessage())
                    .results(Collections.emptyList())
                    .build();
        } finally {
            if (tempDir != null) {
                deleteDirectory(tempDir.toFile());
            }
        }
    }

    private TestCaseResult runSingleTestCase(Path dir, String code, String lang, String input, String expectedOutput, int index, boolean isHidden) {
        long startTime = System.currentTimeMillis();
        try {
            ProcessBuilder pb;
            File sourceFile;

            switch (lang) {
                case "javascript":
                case "js":
                case "node":
                    sourceFile = new File(dir.toFile(), "solution.js");
                    Files.writeString(sourceFile.toPath(), code, StandardCharsets.UTF_8);
                    pb = new ProcessBuilder("node", sourceFile.getAbsolutePath());
                    break;

                case "java":
                    String className = extractJavaClassName(code);
                    sourceFile = new File(dir.toFile(), className + ".java");
                    Files.writeString(sourceFile.toPath(), code, StandardCharsets.UTF_8);

                    // Compile first
                    ProcessBuilder compilePb = new ProcessBuilder("javac", sourceFile.getAbsolutePath());
                    compilePb.directory(dir.toFile());
                    Process compileProc = compilePb.start();
                    boolean compileOk = compileProc.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    if (!compileOk || compileProc.exitValue() != 0) {
                        String compileErr = readStream(compileProc.getErrorStream());
                        return TestCaseResult.builder()
                                .testCaseNumber(index)
                                .input(input)
                                .expectedOutput(expectedOutput)
                                .actualOutput("")
                                .passed(false)
                                .error("Compilation error: " + compileErr)
                                .executionTimeMs(System.currentTimeMillis() - startTime)
                                .isHidden(isHidden)
                                .build();
                    }
                    pb = new ProcessBuilder("java", "-cp", dir.toFile().getAbsolutePath(), className);
                    break;

                case "python":
                case "py":
                default:
                    sourceFile = new File(dir.toFile(), "solution.py");
                    Files.writeString(sourceFile.toPath(), code, StandardCharsets.UTF_8);
                    pb = new ProcessBuilder("python", sourceFile.getAbsolutePath());
                    break;
            }

            pb.directory(dir.toFile());
            Process process = pb.start();

            // Provide input via stdin
            if (input != null && !input.isEmpty()) {
                try (OutputStream os = process.getOutputStream();
                     BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(os, StandardCharsets.UTF_8))) {
                    writer.write(input);
                    writer.flush();
                }
            } else {
                process.getOutputStream().close();
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            long executionTime = System.currentTimeMillis() - startTime;

            if (!finished) {
                process.destroyForcibly();
                return TestCaseResult.builder()
                        .testCaseNumber(index)
                        .input(input)
                        .expectedOutput(expectedOutput)
                        .actualOutput("")
                        .passed(false)
                        .error("Time Limit Exceeded (> " + TIMEOUT_SECONDS + "s)")
                        .executionTimeMs(executionTime)
                        .isHidden(isHidden)
                        .build();
            }

            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());

            if (process.exitValue() != 0 && !stderr.isBlank()) {
                return TestCaseResult.builder()
                        .testCaseNumber(index)
                        .input(input)
                        .expectedOutput(expectedOutput)
                        .actualOutput(stdout.trim())
                        .passed(false)
                        .error("Runtime error: " + stderr.trim())
                        .executionTimeMs(executionTime)
                        .isHidden(isHidden)
                        .build();
            }

            String actualTrimmed = stdout.replaceAll("\\r\\n", "\n").trim();
            String expectedTrimmed = expectedOutput != null ? expectedOutput.replaceAll("\\r\\n", "\n").trim() : "";
            boolean passed = actualTrimmed.equals(expectedTrimmed);

            return TestCaseResult.builder()
                    .testCaseNumber(index)
                    .input(input)
                    .expectedOutput(expectedOutput)
                    .actualOutput(stdout.trim())
                    .passed(passed)
                    .error(passed ? null : "Output mismatch")
                    .executionTimeMs(executionTime)
                    .isHidden(isHidden)
                    .build();

        } catch (Exception e) {
            return TestCaseResult.builder()
                    .testCaseNumber(index)
                    .input(input)
                    .expectedOutput(expectedOutput)
                    .actualOutput("")
                    .passed(false)
                    .error("Execution error: " + e.getMessage())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .isHidden(isHidden)
                    .build();
        }
    }

    private String extractJavaClassName(String code) {
        if (code.contains("class ")) {
            int idx = code.indexOf("class ") + 6;
            StringBuilder sb = new StringBuilder();
            while (idx < code.length() && Character.isWhitespace(code.charAt(idx))) idx++;
            while (idx < code.length() && (Character.isLetterOrDigit(code.charAt(idx)) || code.charAt(idx) == '_')) {
                sb.append(code.charAt(idx++));
            }
            if (!sb.isEmpty()) return sb.toString();
        }
        return "Solution";
    }

    private String readStream(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int nRead;
        while ((nRead = is.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }

    private void deleteDirectory(File f) {
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (files != null) {
                for (File child : files) deleteDirectory(child);
            }
        }
        f.delete();
    }
}

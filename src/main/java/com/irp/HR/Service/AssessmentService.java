package com.irp.HR.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.irp.HR.Dto.*;
import com.irp.HR.Exception.*;
import com.irp.HR.Model.*;
import com.irp.HR.Repository.AssessmentRepository;
import com.irp.HR.Repository.CandidateAnswerRepository;
import com.irp.HR.Repository.JobRepository;
import com.irp.HR.Repository.ResumeRepository;
import com.irp.HR.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final AssessmentGeneratorService generatorService;
    private final CodeExecutionService codeExecutionService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${assessment.cooldown-days:30}")
    private int cooldownDays;

    @Value("${assessment.start-window-hours:48}")
    private int startWindowHours;

    @Value("${assessment.tab-switch-flag-threshold:1}")
    private int tabSwitchFlagThreshold;

    private static final int QUESTION_COUNT = 20;
    private static final int TEST_DURATION_MINUTES = 60; // time limit once the test is started

    // ---------------------------------------------------------------
    // 1. GENERATE
    // ---------------------------------------------------------------
    @Transactional
    public AssessmentResponseDTO generateAssessment(UUID candidateId, UUID jobId) {
        assessmentRepository.findTopByCandidateIdAndJobIdOrderByCreatedAtDesc(candidateId, jobId)
                .ifPresent(previous -> {
                    LocalDateTime cooldownEnds = previous.getCreatedAt().plusDays(cooldownDays);
                    if (cooldownEnds.isAfter(LocalDateTime.now())) {
                        throw new CooldownActiveException(cooldownEnds);
                    }
                });

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));

        Integer years = resumeRepository.findTopByCandidateIdOrderByUploadedAtDesc(candidateId)
                .map(Resume::getYearsOfExperience)
                .orElse(null);

        DifficultyLevel difficulty = resolveDifficulty(years);

        List<GeneratedQuestion> generated = generatorService.generateQuestions(
                job.getTitle(), job.getDescription(), difficulty, years);

        Assessment assessment = Assessment.builder()
                .candidateId(candidateId)
                .jobId(jobId)
                .difficultyLevel(difficulty)
                .status(AssessmentStatus.GENERATED)
                .totalQuestions(generated.size())
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(startWindowHours))
                .tabSwitchCount(0)
                .flaggedForCheating(false)
                .build();

        List<AssessmentQuestion> questionEntities = new ArrayList<>();
        int index = 0;
        for (GeneratedQuestion gq : generated) {
            questionEntities.add(AssessmentQuestion.builder()
                    .assessment(assessment)
                    .questionType(gq.questionType() != null ? gq.questionType() : QuestionType.MCQ)
                    .questionText(gq.questionText())
                    .optionA(gq.optionA())
                    .optionB(gq.optionB())
                    .optionC(gq.optionC())
                    .optionD(gq.optionD())
                    .correctOption(gq.correctOption())
                    .starterCode(gq.starterCode())
                    .testCases(gq.testCases())
                    .allowedLanguages(gq.allowedLanguages())
                    .orderIndex(index++)
                    .build());
        }
        assessment.setQuestions(questionEntities);

        Assessment saved = assessmentRepository.save(assessment);
        return toResponseDTO(saved);
    }

    // ---------------------------------------------------------------
    // 2. START (candidate opens the assessment page)
    // ---------------------------------------------------------------
    @Transactional
    public AssessmentResponseDTO startAssessment(UUID assessmentId, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(assessmentId, candidateId);

        if (assessment.getStatus() == AssessmentStatus.GENERATED
                && assessment.getExpiresAt().isBefore(LocalDateTime.now())) {
            assessment.setStatus(AssessmentStatus.EXPIRED);
            assessmentRepository.save(assessment);
        }

        if (assessment.getStatus() == AssessmentStatus.EXPIRED) {
            throw new InvalidAssessmentStateException("This assessment has expired.");
        }
        if (assessment.getStatus() == AssessmentStatus.COMPLETED) {
            throw new InvalidAssessmentStateException("This assessment was already submitted.");
        }
        if (assessment.getStatus() == AssessmentStatus.GENERATED) {
            assessment.setStatus(AssessmentStatus.IN_PROGRESS);
            assessment.setStartedAt(LocalDateTime.now());
            assessment.setExpiresAt(LocalDateTime.now().plusMinutes(TEST_DURATION_MINUTES));
            assessmentRepository.save(assessment);
        }

        return toResponseDTO(assessment);
    }

    // ---------------------------------------------------------------
    // 3. SUBMIT (server-side scoring for both MCQs & Coding Questions)
    // ---------------------------------------------------------------
    @Transactional
    public AssessmentResultDTO submitAssessment(SubmitAssessmentRequest request, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(request.getAssessmentId(), candidateId);

        if (assessment.getStatus() != AssessmentStatus.IN_PROGRESS) {
            throw new InvalidAssessmentStateException(
                    "Assessment cannot be submitted from status: " + assessment.getStatus());
        }

        Map<UUID, AssessmentQuestion> questionsById = assessment.getQuestions().stream()
                .collect(Collectors.toMap(AssessmentQuestion::getId, q -> q));

        int correctCount = 0;
        for (SubmitAnswerDTO answerDTO : request.getAnswers()) {
            AssessmentQuestion question = questionsById.get(answerDTO.getQuestionId());
            if (question == null) {
                continue;
            }

            if (question.getQuestionType() == QuestionType.CODING) {
                List<CodeExecutionService.TestCaseDefinition> testCases =
                        codeExecutionService.parseTestCases(question.getTestCases());

                String code = answerDTO.getSubmittedCode() != null ? answerDTO.getSubmittedCode().trim() : "";
                String lang = answerDTO.getSelectedLanguage() != null ? answerDTO.getSelectedLanguage() : "python";

                int passed = 0;
                int total = testCases.size();
                boolean isCorrect = false;
                String output = "No code submitted";

                // Check if code was actually written and not just the unedited TODO template
                boolean hasUserCode = !code.isEmpty()
                        && !code.contains("# TODO: Write your solution here")
                        && !code.contains("// TODO: Write your solution here");

                if (hasUserCode) {
                    CodeRunResponse exec = codeExecutionService.execute(code, lang, testCases, false);
                    passed = exec.getPassedCount();
                    total = exec.getTotalCount();
                    isCorrect = (total > 0 && passed == total);
                    output = passed + "/" + total + " test cases passed";
                }

                if (isCorrect) {
                    correctCount++;
                }

                candidateAnswerRepository.save(CandidateAnswer.builder()
                        .assessment(assessment)
                        .question(question)
                        .submittedCode(code)
                        .selectedLanguage(lang)
                        .passedTestCases(passed)
                        .totalTestCases(total)
                        .executionOutput(output)
                        .correct(isCorrect)
                        .build());

            } else {
                String selected = answerDTO.getSelectedOption() != null ? answerDTO.getSelectedOption().toUpperCase() : "";
                boolean isCorrect = question.getCorrectOption() != null && question.getCorrectOption().equalsIgnoreCase(selected);
                if (isCorrect) {
                    correctCount++;
                }

                candidateAnswerRepository.save(CandidateAnswer.builder()
                        .assessment(assessment)
                        .question(question)
                        .selectedOption(selected)
                        .correct(isCorrect)
                        .build());
            }
        }

        int tabSwitches = request.getTabSwitchCount() != null ? request.getTabSwitchCount() : 0;
        int totalQuestions = assessment.getTotalQuestions();
        int score = totalQuestions > 0 ? Math.round((correctCount * 100.0f) / totalQuestions) : 0;

        assessment.setCorrectCount(correctCount);
        assessment.setScore(score);
        assessment.setStatus(AssessmentStatus.COMPLETED);
        assessment.setSubmittedAt(LocalDateTime.now());
        assessment.setTabSwitchCount(tabSwitches);
        assessment.setFlaggedForCheating(tabSwitches >= tabSwitchFlagThreshold);

        Assessment saved = assessmentRepository.save(assessment);

        return AssessmentResultDTO.builder()
                .assessmentId(saved.getId())
                .totalQuestions(saved.getTotalQuestions())
                .correctCount(saved.getCorrectCount())
                .score(saved.getScore())
                .status(saved.getStatus())
                .submittedAt(saved.getSubmittedAt())
                .tabSwitchCount(saved.getTabSwitchCount())
                .flaggedForCheating(saved.getFlaggedForCheating())
                .build();
    }

    // ---------------------------------------------------------------
    // 4. RUN CODE (test code live against visible sample test cases)
    // ---------------------------------------------------------------
    public CodeRunResponse runCandidateCode(UUID assessmentId, RunCodeRequest request, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(assessmentId, candidateId);

        AssessmentQuestion question = assessment.getQuestions().stream()
                .filter(q -> q.getId().equals(request.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question not found in assessment"));

        if (question.getQuestionType() != QuestionType.CODING) {
            throw new IllegalArgumentException("Question is not a coding question");
        }

        List<CodeExecutionService.TestCaseDefinition> testCases =
                codeExecutionService.parseTestCases(question.getTestCases());

        // Run ONLY visible sample test cases
        return codeExecutionService.execute(request.getCode(), request.getLanguage(), testCases, true);
    }

    // ---------------------------------------------------------------
    // 5. FETCH (resume an in-progress assessment; still no correct answers)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public AssessmentResponseDTO getAssessment(UUID assessmentId, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(assessmentId, candidateId);
        return toResponseDTO(assessment);
    }

    // ---------------------------------------------------------------
    // 6. REVIEW (candidate sees correct vs. selected answers, post-submit only)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public AssessmentReviewDTO getAssessmentReview(UUID assessmentId, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(assessmentId, candidateId);

        if (assessment.getStatus() != AssessmentStatus.COMPLETED) {
            throw new InvalidAssessmentStateException(
                    "Review is only available after submission. Current status: " + assessment.getStatus());
        }

        Map<UUID, CandidateAnswer> answersByQuestionId = assessment.getAnswers().stream()
                .collect(Collectors.toMap(a -> a.getQuestion().getId(), a -> a));

        List<QuestionReviewDTO> reviewDTOs = assessment.getQuestions().stream()
                .sorted(Comparator.comparing(AssessmentQuestion::getOrderIndex))
                .map(q -> {
                    CandidateAnswer answer = answersByQuestionId.get(q.getId());
                    return QuestionReviewDTO.builder()
                            .id(q.getId())
                            .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : "MCQ")
                            .questionText(q.getQuestionText())
                            .optionA(q.getOptionA())
                            .optionB(q.getOptionB())
                            .optionC(q.getOptionC())
                            .optionD(q.getOptionD())
                            .correctOption(q.getCorrectOption())
                            .selectedOption(answer != null ? answer.getSelectedOption() : null)
                            .submittedCode(answer != null ? answer.getSubmittedCode() : null)
                            .selectedLanguage(answer != null ? answer.getSelectedLanguage() : null)
                            .passedTestCases(answer != null ? answer.getPassedTestCases() : null)
                            .totalTestCases(answer != null ? answer.getTotalTestCases() : null)
                            .executionOutput(answer != null ? answer.getExecutionOutput() : null)
                            .isCorrect(answer != null ? answer.getCorrect() : false)
                            .orderIndex(q.getOrderIndex())
                            .build();
                })
                .collect(Collectors.toList());

        return AssessmentReviewDTO.builder()
                .assessmentId(assessment.getId())
                .jobId(assessment.getJobId())
                .difficultyLevel(assessment.getDifficultyLevel())
                .status(assessment.getStatus())
                .totalQuestions(assessment.getTotalQuestions())
                .correctCount(assessment.getCorrectCount())
                .score(assessment.getScore())
                .submittedAt(assessment.getSubmittedAt())
                .questions(reviewDTOs)
                .build();
    }

    // ---------------------------------------------------------------
    // 7. RECRUITER VIEW - all candidates' results for a job, filterable
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public List<CandidateAssessmentSummaryDTO> getJobAssessments(
            UUID jobId, AssessmentStatus status, Integer minScore, Integer maxScore, Boolean flagged) {

        List<Assessment> assessments = assessmentRepository.findByJobId(jobId);

        return assessments.stream()
                .filter(a -> status == null || a.getStatus() == status)
                .filter(a -> minScore == null || (a.getScore() != null && a.getScore() >= minScore))
                .filter(a -> maxScore == null || (a.getScore() != null && a.getScore() <= maxScore))
                .filter(a -> flagged == null || flagged.equals(a.getFlaggedForCheating()))
                .sorted(Comparator.comparing(
                        (Assessment a) -> a.getScore() == null ? -1 : a.getScore()).reversed())
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());
    }

    private CandidateAssessmentSummaryDTO toSummaryDTO(Assessment assessment) {
        User candidate = userRepository.findById(assessment.getCandidateId()).orElse(null);

        return CandidateAssessmentSummaryDTO.builder()
                .assessmentId(assessment.getId())
                .candidateId(assessment.getCandidateId())
                .candidateName(candidate != null ? candidate.getName() : "Unknown")
                .candidateEmail(candidate != null ? candidate.getEmail() : "Unknown")
                .difficultyLevel(assessment.getDifficultyLevel())
                .status(assessment.getStatus())
                .totalQuestions(assessment.getTotalQuestions())
                .correctCount(assessment.getCorrectCount())
                .score(assessment.getScore())
                .tabSwitchCount(assessment.getTabSwitchCount())
                .flaggedForCheating(assessment.getFlaggedForCheating())
                .createdAt(assessment.getCreatedAt())
                .submittedAt(assessment.getSubmittedAt())
                .build();
    }

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------
    private Assessment getOwnedAssessment(UUID assessmentId, UUID candidateId) {
        Assessment assessment = assessmentRepository.findById(assessmentId)
                .orElseThrow(() -> new AssessmentNotFoundException(assessmentId));
        if (!assessment.getCandidateId().equals(candidateId)) {
            throw new UnauthorizedAssessmentAccessException();
        }
        return assessment;
    }

    private DifficultyLevel resolveDifficulty(Integer years) {
        if (years == null) {
            return DifficultyLevel.EASY;
        } else if (years < 2) {
            return DifficultyLevel.EASY;
        } else if (years <= 5) {
            return DifficultyLevel.MEDIUM;
        } else {
            return DifficultyLevel.HARD;
        }
    }

    private String extractSampleTestCases(String testCasesJson) {
        if (testCasesJson == null || testCasesJson.isBlank()) return "[]";
        try {
            List<CodeExecutionService.TestCaseDefinition> all = codeExecutionService.parseTestCases(testCasesJson);
            List<Map<String, String>> samples = new ArrayList<>();
            for (CodeExecutionService.TestCaseDefinition tc : all) {
                if (!tc.isHidden) {
                    samples.add(Map.of(
                            "input", tc.input != null ? tc.input : "",
                            "expectedOutput", tc.expectedOutput != null ? tc.expectedOutput : ""
                    ));
                }
            }
            return objectMapper.writeValueAsString(samples);
        } catch (Exception e) {
            return "[]";
        }
    }

    private AssessmentResponseDTO toResponseDTO(Assessment assessment) {
        List<QuestionDTO> questionDTOs = assessment.getQuestions().stream()
                .sorted(Comparator.comparing(AssessmentQuestion::getOrderIndex))
                .map(q -> {
                    String sampleCases = null;
                    if (q.getQuestionType() == QuestionType.CODING && q.getTestCases() != null) {
                        sampleCases = extractSampleTestCases(q.getTestCases());
                    }
                    return QuestionDTO.builder()
                            .id(q.getId())
                            .questionType(q.getQuestionType() != null ? q.getQuestionType().name() : "MCQ")
                            .questionText(q.getQuestionText())
                            .optionA(q.getOptionA())
                            .optionB(q.getOptionB())
                            .optionC(q.getOptionC())
                            .optionD(q.getOptionD())
                            .starterCode(q.getStarterCode())
                            .allowedLanguages(q.getAllowedLanguages())
                            .sampleTestCases(sampleCases)
                            .orderIndex(q.getOrderIndex())
                            .build();
                })
                .collect(Collectors.toList());

        return AssessmentResponseDTO.builder()
                .assessmentId(assessment.getId())
                .jobId(assessment.getJobId())
                .difficultyLevel(assessment.getDifficultyLevel())
                .status(assessment.getStatus())
                .totalQuestions(assessment.getTotalQuestions())
                .expiresAt(assessment.getExpiresAt())
                .questions(questionDTOs)
                .build();
    }
}
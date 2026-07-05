package com.irp.HR.Service;

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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AssessmentService {

    private final AssessmentRepository assessmentRepository;
    private final CandidateAnswerRepository candidateAnswerRepository;
    private final AssessmentGeneratorService generatorService;
    private final JobRepository jobRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;

    @Value("${assessment.cooldown-days:30}")
    private int cooldownDays;

    @Value("${assessment.start-window-hours:48}")
    private int startWindowHours;

    @Value("${assessment.tab-switch-flag-threshold:3}")
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
                .orElseThrow(() -> new IllegalArgumentException("Job not found with id: " + jobId));

        // Pull years of experience ONCE and reuse it for both the difficulty
        // tier AND the concrete calibration text sent to the model, so the
        // two stay in sync (previously only the tier label was passed on,
        // and the model never saw the actual number).
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
                .totalQuestions(generated.size())   // use actual count (may be 15-20)
                .createdAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusHours(startWindowHours)) // window to START
                .tabSwitchCount(0)
                .flaggedForCheating(false)
                .build();

        List<AssessmentQuestion> questionEntities = new ArrayList<>();
        int index = 0;
        for (GeneratedQuestion gq : generated) {
            questionEntities.add(AssessmentQuestion.builder()
                    .assessment(assessment)
                    .questionText(gq.questionText())
                    .optionA(gq.optionA())
                    .optionB(gq.optionB())
                    .optionC(gq.optionC())
                    .optionD(gq.optionD())
                    .correctOption(gq.correctOption())
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
            // Override expiresAt to a fixed test-taking window from NOW
            assessment.setExpiresAt(LocalDateTime.now().plusMinutes(TEST_DURATION_MINUTES));
            assessmentRepository.save(assessment);
        }

        return toResponseDTO(assessment);
    }

    // ---------------------------------------------------------------
    // 3. SUBMIT (server-side scoring only)
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
                continue; // ignore answers for questions not belonging to this assessment
            }
            boolean isCorrect = question.getCorrectOption().equalsIgnoreCase(answerDTO.getSelectedOption());
            if (isCorrect) {
                correctCount++;
            }
            candidateAnswerRepository.save(CandidateAnswer.builder()
                    .assessment(assessment)
                    .question(question)
                    .selectedOption(answerDTO.getSelectedOption().toUpperCase())
                    .correct(isCorrect)
                    .build());
        }

        int tabSwitches = request.getTabSwitchCount() != null ? request.getTabSwitchCount() : 0;
        int totalQuestions = assessment.getTotalQuestions();
        int score = Math.round((correctCount * 100.0f) / totalQuestions);

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
    // 4. FETCH (resume an in-progress assessment; still no correct answers)
    // ---------------------------------------------------------------
    @Transactional(readOnly = true)
    public AssessmentResponseDTO getAssessment(UUID assessmentId, UUID candidateId) {
        Assessment assessment = getOwnedAssessment(assessmentId, candidateId);
        return toResponseDTO(assessment);
    }

    // ---------------------------------------------------------------
    // 5. REVIEW (candidate sees correct vs. selected answers, post-submit only)
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
                            .questionText(q.getQuestionText())
                            .optionA(q.getOptionA())
                            .optionB(q.getOptionB())
                            .optionC(q.getOptionC())
                            .optionD(q.getOptionD())
                            .correctOption(q.getCorrectOption())
                            .selectedOption(answer != null ? answer.getSelectedOption() : null)
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
    // 6. RECRUITER VIEW - all candidates' results for a job, filterable
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

    /**
     * Maps years of experience to a difficulty tier.
     * No resume uploaded yet, or the model couldn't tell -> defaults to EASY.
     *
     * years == null or < 2  -> EASY   (fresher / beginner)
     * years 2-5             -> MEDIUM (intermediate)
     * years > 5             -> HARD   (senior)
     */
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

    private AssessmentResponseDTO toResponseDTO(Assessment assessment) {
        List<QuestionDTO> questionDTOs = assessment.getQuestions().stream()
                .sorted((a, b) -> a.getOrderIndex().compareTo(b.getOrderIndex()))
                .map(q -> QuestionDTO.builder()
                        .id(q.getId())
                        .questionText(q.getQuestionText())
                        .optionA(q.getOptionA())
                        .optionB(q.getOptionB())
                        .optionC(q.getOptionC())
                        .optionD(q.getOptionD())
                        .orderIndex(q.getOrderIndex())
                        .build())
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
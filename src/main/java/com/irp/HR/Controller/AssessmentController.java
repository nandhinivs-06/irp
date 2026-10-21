package com.irp.HR.Controller;

import com.irp.HR.Dto.AssessmentResponseDTO;
import com.irp.HR.Dto.AssessmentResultDTO;
import com.irp.HR.Dto.AssessmentReviewDTO;
import com.irp.HR.Dto.CandidateAssessmentSummaryDTO;
import com.irp.HR.Dto.GenerateAssessmentRequest;
import com.irp.HR.Dto.SubmitAssessmentRequest;
import com.irp.HR.Model.AssessmentStatus;
import com.irp.HR.Security.UserPrincipal;
import com.irp.HR.Service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    @PostMapping("/generate")
    public ResponseEntity<AssessmentResponseDTO> generate(
            @Valid @RequestBody GenerateAssessmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.generateAssessment(principal.getId(), request.getJobId()));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<AssessmentResponseDTO> start(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.startAssessment(id, principal.getId()));
    }

    @PostMapping("/submit")
    public ResponseEntity<AssessmentResultDTO> submit(
            @Valid @RequestBody SubmitAssessmentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.submitAssessment(request, principal.getId()));
    }

    @PostMapping("/{id}/run-code")
    public ResponseEntity<com.irp.HR.Dto.CodeRunResponse> runCode(
            @PathVariable UUID id,
            @Valid @RequestBody com.irp.HR.Dto.RunCodeRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.runCandidateCode(id, request, principal.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AssessmentResponseDTO> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.getAssessment(id, principal.getId()));
    }

    @GetMapping("/{id}/review")
    public ResponseEntity<AssessmentReviewDTO> review(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.getAssessmentReview(id, principal.getId()));
    }

    /** RECRUITER — list all candidate assessment results for a job with optional filters */
    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<List<CandidateAssessmentSummaryDTO>> getJobAssessments(
            @PathVariable UUID jobId,
            @RequestParam(required = false) AssessmentStatus status,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(required = false) Boolean flagged) {
        return ResponseEntity.ok(
                assessmentService.getJobAssessments(jobId, status, minScore, maxScore, flagged));
    }
}

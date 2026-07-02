package com.irp.HR.Controller;

import com.irp.HR.Dto.AssessmentResponseDTO;
import com.irp.HR.Dto.AssessmentResultDTO;
import com.irp.HR.Dto.GenerateAssessmentRequest;
import com.irp.HR.Dto.SubmitAssessmentRequest;
import com.irp.HR.Security.UserPrincipal;
import com.irp.HR.Service.AssessmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/{id}")
    public ResponseEntity<AssessmentResponseDTO> get(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(assessmentService.getAssessment(id, principal.getId()));
    }
}

package com.irp.HR.Controller;


import com.irp.HR.DTO.JobRequest;
import com.irp.HR.DTO.JobResponse;
import com.irp.HR.Dto.CandidateAssessmentSummaryDTO;
import com.irp.HR.Model.AssessmentStatus;
import com.irp.HR.Model.Job;
import com.irp.HR.Model.JobStatus;
import com.irp.HR.Service.AssessmentService;
import com.irp.HR.Service.JobService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/jobs")
@RequiredArgsConstructor
public class JobController {

    private final JobService jobService;
    private final AssessmentService assessmentService;

    /**
     * Recruiter creates a job posting: title, pasted JD text, and the
     * required skills box. Always starts in DRAFT — recruiter activates
     * it explicitly via PATCH /jobs/{id}/status.
     */
    @PostMapping
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        Job job = jobService.createJob(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(JobResponse.from(job));
    }

    /**
     * Public listing. ?status=ACTIVE to filter; omit to see all.
     * Pageable picks up ?page= and ?size= automatically.
     */
    @GetMapping
    public ResponseEntity<Page<JobResponse>> listJobs(
            @RequestParam(required = false) JobStatus status,
            Pageable pageable
    ) {
        Page<JobResponse> jobs = jobService.listJobs(status, pageable).map(JobResponse::from);
        return ResponseEntity.ok(jobs);
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<JobResponse> getJob(@PathVariable UUID jobId) {
        Job job = jobService.getJobOrThrow(jobId);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    /**
     * Lifecycle transition: DRAFT -> ACTIVE -> CLOSED (FR-08).
     * Example: PATCH /jobs/{jobId}/status?status=ACTIVE
     */
    @PatchMapping("/{jobId}/status")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<JobResponse> updateStatus(
            @PathVariable UUID jobId,
            @RequestParam JobStatus status
    ) {
        Job job = jobService.updateStatus(jobId, status);
        return ResponseEntity.ok(JobResponse.from(job));
    }

    /**
     * All candidates' assessment results for this job, recruiter-only.
     * Sorted by score descending (unscored/in-progress attempts last).
     * All filters are optional and combine with AND:
     *   ?status=COMPLETED
     *   ?minScore=50&maxScore=100
     *   ?flagged=true   (only attempts flagged for tab-switching)
     * Example: GET /jobs/{jobId}/assessments?status=COMPLETED&minScore=70
     */
    @GetMapping("/{jobId}/assessments")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<CandidateAssessmentSummaryDTO>> getJobAssessments(
            @PathVariable UUID jobId,
            @RequestParam(required = false) AssessmentStatus status,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Integer maxScore,
            @RequestParam(required = false) Boolean flagged
    ) {
        return ResponseEntity.ok(assessmentService.getJobAssessments(jobId, status, minScore, maxScore, flagged));
    }
}
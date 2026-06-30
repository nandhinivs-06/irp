package com.irp.HR.Controller;

import com.irp.HR.Dto.MatchReportResponse;
import com.irp.HR.Model.MatchReport;
import com.irp.HR.Service.MatchService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class MatchController {

    private final MatchService matchService;

    /**
     * Triggers the comparison: this resume's extracted skills vs that
     * job's required skills. No role restriction - either the candidate
     * (checking their own fit) or a recruiter (screening a resume) can
     * call this.
     */
    @PostMapping("/resumes/{resumeId}/match")
    public ResponseEntity<MatchReportResponse> generateMatch(
            @PathVariable UUID resumeId,
            @RequestParam UUID jobId
    ) {
        MatchReport report = matchService.generateReport(resumeId, jobId);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(report));
    }

    /** Candidate's view: every match report generated for one of their resumes. */
    @GetMapping("/resumes/{resumeId}/matches")
    public ResponseEntity<List<MatchReportResponse>> getMatchesForResume(@PathVariable UUID resumeId) {
        List<MatchReportResponse> reports = matchService.getReportsForResume(resumeId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(reports);
    }

    /** Recruiter's view: every candidate's match score for one job posting. */
    @GetMapping("/jobs/{jobId}/matches")
    @PreAuthorize("hasRole('RECRUITER')")
    public ResponseEntity<List<MatchReportResponse>> getMatchesForJob(@PathVariable UUID jobId) {
        List<MatchReportResponse> reports = matchService.getReportsForJob(jobId)
                .stream().map(this::toResponse).toList();
        return ResponseEntity.ok(reports);
    }

    private MatchReportResponse toResponse(MatchReport report) {
        return new MatchReportResponse(
                report.getId(),
                report.getResumeId(),
                report.getJobId(),
                report.getMatchScore(),
                report.getMatchedSkills(),
                report.getMissingSkills(),
                report.getCreatedAt()
        );
    }
}

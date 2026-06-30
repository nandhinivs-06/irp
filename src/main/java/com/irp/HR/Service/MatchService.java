package com.irp.HR.Service;

import com.irp.HR.Model.Job;
import com.irp.HR.Model.MatchReport;
import com.irp.HR.Model.Resume;
import com.irp.HR.Repository.MatchReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Compares a resume's extracted skills against a job's required skills.
 *
 * Deliberately NOT done via the LLM. Pulling skills out of messy resume
 * text needs AI (OllamaService handles that part) - but once both sides
 * are just comma-separated skill lists, a plain set comparison is instant,
 * free, deterministic, and you can actually explain the number to someone
 * who asks "why is this 60%?" An AI-generated score would be neither
 * deterministic nor easily explainable.
 */
@Service
@RequiredArgsConstructor
public class MatchService {

    private final MatchReportRepository matchReportRepository;
    private final ResumeService resumeService;
    private final JobService jobService;

    @Transactional
    public MatchReport generateReport(UUID resumeId, UUID jobId) {
        Resume resume = resumeService.getResumeOrThrow(resumeId);
        Job job = jobService.getJobOrThrow(jobId);

        Set<String> requiredSkills = normalize(job.getRequiredSkills());
        Set<String> candidateSkills = normalize(resume.getExtractedSkills());

        Set<String> matched = new LinkedHashSet<>(requiredSkills);
        matched.retainAll(candidateSkills);

        Set<String> missing = new LinkedHashSet<>(requiredSkills);
        missing.removeAll(candidateSkills);

        double score = requiredSkills.isEmpty()
                ? 0.0
                : Math.round((matched.size() * 10000.0) / requiredSkills.size()) / 100.0;

        MatchReport report = MatchReport.create(
                resumeId,
                jobId,
                score,
                String.join(", ", matched),
                String.join(", ", missing)
        );
        return matchReportRepository.save(report);
    }

    public List<MatchReport> getReportsForResume(UUID resumeId) {
        return matchReportRepository.findByResumeId(resumeId);
    }

    public List<MatchReport> getReportsForJob(UUID jobId) {
        return matchReportRepository.findByJobId(jobId);
    }

    /** lowercase + trim each comma-separated entry so "Java" and "java " match. */
    private Set<String> normalize(String commaSeparated) {
        if (commaSeparated == null || commaSeparated.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(commaSeparated.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}

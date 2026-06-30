package com.irp.HR.Model;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * The persisted result of comparing one resume's extracted skills against
 * one job's required skills. Computed once when generateMatch is called,
 * not recalculated on every page view - this is what both the candidate
 * and the recruiter end up looking at.
 */
@Entity
@Table(name = "match_reports")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MatchReport {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "match_score", nullable = false)
    private double matchScore; // 0-100, percentage of required skills the resume covers

    @Lob
    @Column(name = "matched_skills", columnDefinition = "TEXT")
    private String matchedSkills; // comma-separated

    @Lob
    @Column(name = "missing_skills", columnDefinition = "TEXT")
    private String missingSkills; // comma-separated - required but not found on the resume

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static MatchReport create(UUID resumeId, UUID jobId, double matchScore,
                                     String matchedSkills, String missingSkills) {
        MatchReport report = new MatchReport();
        report.resumeId = resumeId;
        report.jobId = jobId;
        report.matchScore = matchScore;
        report.matchedSkills = matchedSkills;
        report.missingSkills = missingSkills;
        return report;
    }
}

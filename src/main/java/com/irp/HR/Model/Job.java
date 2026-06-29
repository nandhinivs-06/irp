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
 * Job postings table.
 *
 * `description` holds the full pasted JD text. `requiredSkills` is a
 * separate box on the form — the recruiter types comma-separated skills
 * (e.g. "Java, Spring Boot, PostgreSQL") so matching logic later can work
 * off a short skill list instead of having to parse the whole JD text.
 */
@Entity
@Table(name = "jobs")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // JPA needs a no-arg ctor; use the factory method below
public class Job {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 255)
    private String title;

    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String description; // pasted JD text

    @Column(name = "required_skills", nullable = false, columnDefinition = "TEXT")
    private String requiredSkills; // comma-separated, e.g. "Java, Spring Boot, PostgreSQL"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Job createDraft(String title, String description, String requiredSkills) {
        Job job = new Job();
        job.title = title;
        job.description = description;
        job.requiredSkills = requiredSkills;
        job.status = JobStatus.DRAFT;
        return job;
    }
}
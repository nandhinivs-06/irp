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
 * NOTE: the original SRS ER diagram only had id/title/status/created_at —
 * no column for the actual JD text. FR-07 says recruiters create jobs
 * "with title, description, and requirements", so `description` is added
 * here to hold the JD text pasted into the textbox on job creation.
 * See db/migration/V2__add_jobs_description.sql if your `jobs` table
 * already exists without this column.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status = JobStatus.DRAFT;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    public static Job createDraft(String title, String description) {
        Job job = new Job();
        job.title = title;
        job.description = description;
        job.status = JobStatus.DRAFT;
        return job;
    }
}

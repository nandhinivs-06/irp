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
 * An uploaded resume file plus the skills extracted from it by OllamaService.
 *
 * candidateId is kept as a plain UUID rather than a @ManyToOne relationship
 * to Candidate - this module doesn't need to know Candidate's exact field
 * layout to compile. Swap to a real relation later if you'd rather have
 * JPA enforce the FK and let you do resume.getCandidate().getName() etc.
 */
@Entity
@Table(name = "resumes")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Resume {

    @Id
    @GeneratedValue
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(name = "candidate_id", nullable = false)
    private UUID candidateId;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    @Column(name = "file_path", nullable = false, columnDefinition = "TEXT")
    private String filePath;

    @Column(name = "extracted_skills", columnDefinition = "TEXT")
    private String extractedSkills; // comma-separated, filled in after Ollama parsing

    @CreationTimestamp
    @Column(name = "uploaded_at", nullable = false, updatable = false)
    private OffsetDateTime uploadedAt;

    public static Resume create(UUID candidateId, String fileName, String filePath) {
        Resume resume = new Resume();
        resume.candidateId = candidateId;
        resume.fileName = fileName;
        resume.filePath = filePath;
        return resume;
    }
}

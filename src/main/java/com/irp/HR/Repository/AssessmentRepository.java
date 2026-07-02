package com.irp.HR.Repository;

import com.irp.HR.Model.Assessment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AssessmentRepository extends JpaRepository<Assessment, UUID> {

    Optional<Assessment> findTopByCandidateIdAndJobIdOrderByCreatedAtDesc(UUID candidateId, UUID jobId);
}

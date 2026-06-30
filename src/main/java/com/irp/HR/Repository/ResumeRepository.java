package com.irp.HR.Repository;

import com.irp.HR.Model.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResumeRepository extends JpaRepository<Resume, UUID> {

    List<Resume> findByCandidateId(UUID candidateId);
}

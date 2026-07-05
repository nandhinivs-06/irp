package com.irp.HR.Repository;

import com.irp.HR.Model.CandidateAnswer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CandidateAnswerRepository extends JpaRepository<CandidateAnswer, UUID> {
}

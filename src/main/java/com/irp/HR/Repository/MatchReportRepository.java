package com.irp.HR.Repository;

import com.irp.HR.Model.MatchReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MatchReportRepository extends JpaRepository<MatchReport, UUID> {

    List<MatchReport> findByResumeId(UUID resumeId);

    List<MatchReport> findByJobId(UUID jobId);

    Optional<MatchReport> findByResumeIdAndJobId(UUID resumeId, UUID jobId);
}

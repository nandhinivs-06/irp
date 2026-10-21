package com.irp.HR.Service;

import com.irp.HR.Dto.JobRequest;
import com.irp.HR.Exception.InvalidJobTransitionException;
import com.irp.HR.Exception.JobNotFoundException;
import com.irp.HR.Model.Job;
import com.irp.HR.Model.JobStatus;
import com.irp.HR.Repository.JobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JobService {

    private final JobRepository jobRepository;

    // Only forward transitions, no skipping, no reverting once closed.
    private static final Map<JobStatus, Set<JobStatus>> VALID_TRANSITIONS = new EnumMap<>(JobStatus.class);

    static {
        VALID_TRANSITIONS.put(JobStatus.DRAFT, EnumSet.of(JobStatus.ACTIVE));
        VALID_TRANSITIONS.put(JobStatus.ACTIVE, EnumSet.of(JobStatus.CLOSED));
        VALID_TRANSITIONS.put(JobStatus.CLOSED, EnumSet.noneOf(JobStatus.class));
    }

    @Transactional
    public Job createJob(JobRequest request) {
        Job job = Job.createDraft(request.title(), request.description(), request.requiredSkills());
        return jobRepository.save(job);
    }

    public Job getJobOrThrow(UUID jobId) {
        return jobRepository.findById(jobId)
                .orElseThrow(() -> new JobNotFoundException(jobId));
    }

    public Page<Job> listJobs(JobStatus statusFilter, Pageable pageable) {
        if (statusFilter != null) {
            return jobRepository.findByStatus(statusFilter, pageable);
        }
        return jobRepository.findAll(pageable);
    }

    @Transactional
    public Job updateStatus(UUID jobId, JobStatus newStatus) {
        Job job = getJobOrThrow(jobId);
        Set<JobStatus> allowed = VALID_TRANSITIONS.get(job.getStatus());

        if (!allowed.contains(newStatus)) {
            throw new InvalidJobTransitionException(job.getStatus(), newStatus, allowed);
        }
        job.setStatus(newStatus);
        return jobRepository.save(job);
    }
}
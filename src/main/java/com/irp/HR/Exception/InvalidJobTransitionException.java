package com.irp.HR.Exception;

import com.irp.HR.Model.JobStatus;

import java.util.Set;

public class InvalidJobTransitionException extends RuntimeException {
    public InvalidJobTransitionException(JobStatus from, JobStatus to, Set<JobStatus> allowed) {
        super(String.format(
                "Cannot move job from '%s' to '%s'. Allowed next state(s): %s",
                from, to, allowed.isEmpty() ? "none — job is closed" : allowed
        ));
    }
}


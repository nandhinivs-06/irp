package com.irp.HR.Exception;

import java.time.LocalDateTime;

public class CooldownActiveException extends RuntimeException {

    private final LocalDateTime nextEligibleAt;

    public CooldownActiveException(LocalDateTime nextEligibleAt) {
        super("An assessment for this job was already attempted. Next eligible date: " + nextEligibleAt);
        this.nextEligibleAt = nextEligibleAt;
    }

    public LocalDateTime getNextEligibleAt() {
        return nextEligibleAt;
    }
}

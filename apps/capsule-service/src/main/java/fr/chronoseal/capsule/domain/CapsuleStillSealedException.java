package fr.chronoseal.capsule.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class CapsuleStillSealedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient Instant unlockAt;
    private final transient Duration remaining;

    public CapsuleStillSealedException(UUID id, Instant unlockAt, Duration remaining) {
        super("La capsule " + id + " reste scellée jusqu'au " + unlockAt + ".");
        this.unlockAt = unlockAt;
        this.remaining = remaining.isNegative() ? Duration.ZERO : remaining;
    }

    public Instant getUnlockAt() {
        return unlockAt;
    }

    public Duration getRemaining() {
        return remaining;
    }
}

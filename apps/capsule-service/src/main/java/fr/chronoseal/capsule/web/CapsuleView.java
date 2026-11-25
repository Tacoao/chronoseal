package fr.chronoseal.capsule.web;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import fr.chronoseal.capsule.domain.Capsule;

public record CapsuleView(
        UUID id,
        String title,
        String author,
        Instant createdAt,
        Instant unlockAt,
        boolean sealed,
        long secondsRemaining,
        String commitment) {

    static CapsuleView of(Capsule capsule, Instant now) {
        boolean sealed = capsule.isSealedAt(now);
        long remaining = sealed ? Duration.between(now, capsule.getUnlockAt()).toSeconds() : 0;
        return new CapsuleView(
                capsule.getId(),
                capsule.getTitle(),
                capsule.getAuthor(),
                capsule.getCreatedAt(),
                capsule.getUnlockAt(),
                sealed,
                remaining,
                capsule.getCommitment());
    }
}

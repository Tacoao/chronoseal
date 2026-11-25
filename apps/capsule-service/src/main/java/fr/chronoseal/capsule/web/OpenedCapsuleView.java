package fr.chronoseal.capsule.web;

import java.time.Instant;
import java.util.UUID;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.service.OpenedCapsule;

public record OpenedCapsuleView(
        UUID id,
        String title,
        String author,
        Instant createdAt,
        Instant unlockAt,
        String message) {

    static OpenedCapsuleView of(OpenedCapsule opened) {
        Capsule capsule = opened.capsule();
        return new OpenedCapsuleView(
                capsule.getId(),
                capsule.getTitle(),
                capsule.getAuthor(),
                capsule.getCreatedAt(),
                capsule.getUnlockAt(),
                opened.message());
    }
}

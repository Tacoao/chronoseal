package fr.chronoseal.capsule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Capsule temporelle : un message qui ne peut être lu qu'à partir de sa date d'ouverture.
 */
public class Capsule {

    private final UUID id;
    private final String title;
    private final String author;
    private final Instant createdAt;
    private final Instant unlockAt;
    private final String message;

    public Capsule(UUID id, String title, String author, Instant createdAt, Instant unlockAt, String message) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.author = author;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.unlockAt = Objects.requireNonNull(unlockAt, "unlockAt");
        this.message = Objects.requireNonNull(message, "message");
    }

    public boolean isSealedAt(Instant now) {
        return now.isBefore(unlockAt);
    }

    public UUID getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUnlockAt() {
        return unlockAt;
    }

    public String getMessage() {
        return message;
    }
}

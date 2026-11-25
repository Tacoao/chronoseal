package fr.chronoseal.capsule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

// Le message n'est jamais stocké en clair, seulement ce que renvoie seal-service.
public class Capsule {

    private final UUID id;
    private final String title;
    private final String author;
    private final Instant createdAt;
    private final Instant unlockAt;
    private final String keyId;
    private final byte[] nonce;
    private final byte[] ciphertext;
    private final String commitment;

    public Capsule(UUID id, String title, String author, Instant createdAt, Instant unlockAt,
            String keyId, byte[] nonce, byte[] ciphertext, String commitment) {
        this.id = Objects.requireNonNull(id, "id");
        this.title = Objects.requireNonNull(title, "title");
        this.author = author;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.unlockAt = Objects.requireNonNull(unlockAt, "unlockAt");
        this.keyId = Objects.requireNonNull(keyId, "keyId");
        this.nonce = Objects.requireNonNull(nonce, "nonce").clone();
        this.ciphertext = Objects.requireNonNull(ciphertext, "ciphertext").clone();
        this.commitment = Objects.requireNonNull(commitment, "commitment");
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

    public String getKeyId() {
        return keyId;
    }

    public byte[] getNonce() {
        return nonce.clone();
    }

    public byte[] getCiphertext() {
        return ciphertext.clone();
    }

    public String getCommitment() {
        return commitment;
    }
}

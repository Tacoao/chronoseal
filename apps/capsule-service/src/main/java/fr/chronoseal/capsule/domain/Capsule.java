package fr.chronoseal.capsule.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import org.springframework.data.domain.Persistable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

// Le message n'est jamais stocké en clair, seulement ce que renvoie seal-service.
@Entity
@Table(name = "capsules", indexes = @Index(name = "idx_capsules_unlock_at", columnList = "unlock_at"))
public class Capsule implements Persistable<UUID> {

    // 64 Kio de message + 16 octets de sel + 16 octets de tag GCM
    public static final int MAX_CIPHERTEXT_LENGTH = 64 * 1024 + 32;

    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(length = 60)
    private String author;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "unlock_at", nullable = false)
    private Instant unlockAt;

    @Column(name = "key_id", nullable = false, length = 64)
    private String keyId;

    @Column(nullable = false, length = 12)
    private byte[] nonce;

    @Column(nullable = false, length = MAX_CIPHERTEXT_LENGTH)
    private byte[] ciphertext;

    @Column(nullable = false, length = 64)
    private String commitment;

    // L'id vient de l'appli, JPA ne peut donc pas deviner seul si l'entité est nouvelle.
    @Transient
    private boolean isNew = true;

    protected Capsule() {
        // pour JPA
    }

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

    @Override
    public UUID getId() {
        return id;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
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

package fr.chronoseal.capsule.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;
import fr.chronoseal.capsule.seal.SealClient;
import fr.chronoseal.capsule.seal.SealTimeLockException;
import fr.chronoseal.capsule.seal.SealedContent;
import fr.chronoseal.capsule.seal.UnsealedContent;

@Service
public class CapsuleService {

    // environ 100 ans
    static final Duration MAX_HORIZON = Duration.ofDays(36_525);

    private final CapsuleStore store;
    private final SealClient sealClient;
    private final Clock clock;

    public CapsuleService(CapsuleStore store, SealClient sealClient, Clock clock) {
        this.store = store;
        this.sealClient = sealClient;
        this.clock = clock;
    }

    public Capsule create(NewCapsule request) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant unlockAt = request.unlockAt().truncatedTo(ChronoUnit.SECONDS);
        if (!unlockAt.isAfter(now)) {
            throw new InvalidUnlockDateException("La date d'ouverture doit être dans le futur.");
        }
        if (unlockAt.isAfter(now.plus(MAX_HORIZON))) {
            throw new InvalidUnlockDateException("La date d'ouverture ne peut pas dépasser cent ans.");
        }
        UUID id = UUID.randomUUID();
        SealedContent sealed = sealClient.seal(id, request.message(), unlockAt);
        Capsule capsule = new Capsule(
                id,
                request.title().strip(),
                normalizeAuthor(request.author()),
                now,
                unlockAt,
                sealed.keyId(),
                sealed.nonce(),
                sealed.ciphertext(),
                sealed.commitment());
        return store.save(capsule);
    }

    public List<Capsule> list() {
        return store.findAllOrderedByUnlockAt();
    }

    public Capsule get(UUID id) {
        return store.findById(id).orElseThrow(() -> new CapsuleNotFoundException(id));
    }

    public OpenedCapsule open(UUID id) {
        Capsule capsule = get(id);
        Instant now = clock.instant();
        if (capsule.isSealedAt(now)) {
            throw stillSealed(capsule, now);
        }
        SealedContent sealed = new SealedContent(
                capsule.getKeyId(), capsule.getNonce(), capsule.getCiphertext(), capsule.getCommitment());
        try {
            UnsealedContent content = sealClient.unseal(id, capsule.getUnlockAt(), sealed);
            return new OpenedCapsule(capsule, content.message(), HexFormat.of().formatHex(content.salt()));
        } catch (SealTimeLockException exception) {
            // Les deux horloges peuvent différer un peu, c'est seal-service qui tranche.
            throw stillSealed(capsule, now);
        }
    }

    private static CapsuleStillSealedException stillSealed(Capsule capsule, Instant now) {
        return new CapsuleStillSealedException(
                capsule.getId(), capsule.getUnlockAt(), Duration.between(now, capsule.getUnlockAt()));
    }

    private static String normalizeAuthor(String author) {
        if (author == null || author.isBlank()) {
            return null;
        }
        return author.strip();
    }
}

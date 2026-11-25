package fr.chronoseal.capsule.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import fr.chronoseal.capsule.domain.Capsule;
import fr.chronoseal.capsule.domain.CapsuleNotFoundException;
import fr.chronoseal.capsule.domain.CapsuleStillSealedException;
import fr.chronoseal.capsule.domain.InvalidUnlockDateException;

@Service
public class CapsuleService {

    // environ 100 ans
    static final Duration MAX_HORIZON = Duration.ofDays(36_525);

    private final CapsuleStore store;
    private final Clock clock;

    public CapsuleService(CapsuleStore store, Clock clock) {
        this.store = store;
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
        Capsule capsule = new Capsule(
                UUID.randomUUID(),
                request.title().strip(),
                normalizeAuthor(request.author()),
                now,
                unlockAt,
                request.message());
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
            throw new CapsuleStillSealedException(id, capsule.getUnlockAt(), Duration.between(now, capsule.getUnlockAt()));
        }
        return new OpenedCapsule(capsule, capsule.getMessage());
    }

    private static String normalizeAuthor(String author) {
        if (author == null || author.isBlank()) {
            return null;
        }
        return author.strip();
    }
}

package fr.chronoseal.capsule.seal;

import java.time.Instant;
import java.util.UUID;

public interface SealClient {

    SealedContent seal(UUID capsuleId, String message, Instant unlockAt);

    UnsealedContent unseal(UUID capsuleId, Instant unlockAt, SealedContent sealed);
}

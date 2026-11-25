package fr.chronoseal.capsule.support;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

import fr.chronoseal.capsule.seal.SealClient;
import fr.chronoseal.capsule.seal.SealTimeLockException;
import fr.chronoseal.capsule.seal.SealedContent;
import fr.chronoseal.capsule.seal.UnsealedContent;

// Faux seal-service : il « chiffre » en inversant les octets.
public final class FakeSealClient implements SealClient {

    public static final byte[] SALT = {1, 2, 3, 4};

    private boolean refuseUnseal;
    private int unsealCalls;

    public void refuseUnseal() {
        this.refuseUnseal = true;
    }

    public int unsealCalls() {
        return unsealCalls;
    }

    @Override
    public SealedContent seal(UUID capsuleId, String message, Instant unlockAt) {
        return new SealedContent("fake-key", new byte[12], reverse(message.getBytes(StandardCharsets.UTF_8)),
                "commitment-of-" + capsuleId);
    }

    @Override
    public UnsealedContent unseal(UUID capsuleId, Instant unlockAt, SealedContent sealed) {
        unsealCalls++;
        if (refuseUnseal) {
            throw new SealTimeLockException("refus simulé", null);
        }
        return new UnsealedContent(new String(reverse(sealed.ciphertext()), StandardCharsets.UTF_8), SALT);
    }

    private static byte[] reverse(byte[] input) {
        byte[] output = new byte[input.length];
        for (int i = 0; i < input.length; i++) {
            output[i] = input[input.length - 1 - i];
        }
        return output;
    }
}

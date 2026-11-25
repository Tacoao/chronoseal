package fr.chronoseal.capsule.seal;

public record SealedContent(String keyId, byte[] nonce, byte[] ciphertext, String commitment) {
}

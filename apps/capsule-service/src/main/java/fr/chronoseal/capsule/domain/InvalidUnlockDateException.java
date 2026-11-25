package fr.chronoseal.capsule.domain;

public class InvalidUnlockDateException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public InvalidUnlockDateException(String message) {
        super(message);
    }
}

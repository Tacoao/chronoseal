package fr.chronoseal.capsule.seal;

public class SealTimeLockException extends SealServiceException {

    private static final long serialVersionUID = 1L;

    public SealTimeLockException(String message, Throwable cause) {
        super(message, cause);
    }
}

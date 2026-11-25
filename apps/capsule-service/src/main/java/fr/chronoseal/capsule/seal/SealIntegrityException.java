package fr.chronoseal.capsule.seal;

public class SealIntegrityException extends SealServiceException {

    private static final long serialVersionUID = 1L;

    public SealIntegrityException(String message, Throwable cause) {
        super(message, cause);
    }
}

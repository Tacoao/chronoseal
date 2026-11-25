package fr.chronoseal.capsule.seal;

public class SealServiceUnavailableException extends SealServiceException {

    private static final long serialVersionUID = 1L;

    public SealServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

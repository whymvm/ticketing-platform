package co.com.ticketing.model.exception;

/**
 * Thrown when another request with the same Idempotency-Key created its order at the same time.
 */
public final class DuplicateRequestException extends RuntimeException {

    public DuplicateRequestException(String key) {
        super("another request with the key " + key + " was processed at the same time", null, false, false);
    }
}

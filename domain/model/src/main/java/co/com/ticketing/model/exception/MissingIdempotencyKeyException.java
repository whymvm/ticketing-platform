package co.com.ticketing.model.exception;

/**
 * Thrown when a request that must be safe to repeat has no Idempotency-Key.
 */
public final class MissingIdempotencyKeyException extends RuntimeException {

    public MissingIdempotencyKeyException() {
        super("the Idempotency-Key header is required", null, false, false);
    }
}

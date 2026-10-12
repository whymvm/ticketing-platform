package co.com.ticketing.model.exception;

/**
 * Thrown when an Idempotency-Key that already created an order comes with a different request.
 */
public final class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException() {
        super("the Idempotency-Key was already used with a different request", null, false, false);
    }
}

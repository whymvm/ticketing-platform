package co.com.ticketing.model.exception;

/**
 * Thrown when the Idempotency-Key does not have the expected format.
 */
public final class InvalidIdempotencyKeyException extends RuntimeException {

    public InvalidIdempotencyKeyException(int minLength, int maxLength) {
        super("the Idempotency-Key must have between " + minLength + " and " + maxLength
                + " letters, digits, hyphens or underscores", null, false, false);
    }
}

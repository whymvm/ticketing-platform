package co.com.ticketing.model.exception;

/**
 * Thrown when another write changed the same data at the same time. The operation can be retried.
 */
public final class ConcurrencyConflictException extends RuntimeException {

    public ConcurrencyConflictException(String id) {
        super("order " + id + " conflicted with a concurrent update", null, false, false);
    }
}

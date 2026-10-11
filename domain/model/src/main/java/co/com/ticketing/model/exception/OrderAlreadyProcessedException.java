package co.com.ticketing.model.exception;

/**
 * Thrown when an order is no longer queued because another process already reserved or rejected it.
 */
public final class OrderAlreadyProcessedException extends RuntimeException {

    public OrderAlreadyProcessedException(String id) {
        super("order " + id + " was already processed", null, false, false);
    }
}

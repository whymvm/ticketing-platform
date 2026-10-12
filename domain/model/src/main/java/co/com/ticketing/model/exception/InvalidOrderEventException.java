package co.com.ticketing.model.exception;

/**
 * Thrown when an order does not name the event it is for.
 */
public final class InvalidOrderEventException extends RuntimeException {

    public InvalidOrderEventException() {
        super("eventId is required", null, false, false);
    }
}

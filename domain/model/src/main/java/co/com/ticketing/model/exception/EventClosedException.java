package co.com.ticketing.model.exception;

/**
 * Thrown when an order is placed for an event that already started, so its sales are closed.
 */
public final class EventClosedException extends RuntimeException {

    public EventClosedException(String eventId) {
        super("event " + eventId + " already started, so its sales are closed", null, false, false);
    }
}

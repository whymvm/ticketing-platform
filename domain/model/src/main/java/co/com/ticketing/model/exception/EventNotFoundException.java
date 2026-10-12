package co.com.ticketing.model.exception;

/**
 * Thrown when no event has the requested id.
 */
public final class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String id) {
        super("no event found with id " + id, null, false, false);
    }
}

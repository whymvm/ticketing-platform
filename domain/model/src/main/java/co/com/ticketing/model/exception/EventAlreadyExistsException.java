package co.com.ticketing.model.exception;

/**
 * Thrown when saving a new event whose id is already stored.
 */
public final class EventAlreadyExistsException extends RuntimeException {

    public EventAlreadyExistsException(String id) {
        super("an event with id " + id + " already exists", null, false, false);
    }
}

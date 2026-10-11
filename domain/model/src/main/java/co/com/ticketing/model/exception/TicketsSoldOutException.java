package co.com.ticketing.model.exception;

/**
 * Thrown when an event does not have enough available tickets for an order.
 */
public final class TicketsSoldOutException extends RuntimeException {

    public TicketsSoldOutException(String eventId, int quantity) {
        super("event " + eventId + " does not have " + quantity + " tickets available", null, false, false);
    }
}

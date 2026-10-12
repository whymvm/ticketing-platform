package co.com.ticketing.model.exception;

/**
 * Thrown when a customer confirms an order whose reservation time is already over.
 */
public final class ReservationExpiredException extends RuntimeException {

    public ReservationExpiredException(String id) {
        super("the reservation of order " + id + " has expired", null, false, false);
    }
}

package co.com.ticketing.model.exception;

/**
 * Thrown when saving a new order whose id is already stored.
 */
public final class OrderAlreadyExistsException extends RuntimeException {

    public OrderAlreadyExistsException(String id) {
        super("an order with id " + id + " already exists", null, false, false);
    }
}

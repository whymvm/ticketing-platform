package co.com.ticketing.model.exception;

/**
 * Thrown when no order has the requested id, or the order belongs to another customer.
 */
public final class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(String id) {
        super("no order found with id " + id, null, false, false);
    }
}

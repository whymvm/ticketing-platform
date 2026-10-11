package co.com.ticketing.model.exception;

import co.com.ticketing.model.order.OrderStatus;

/**
 * Thrown when a customer confirms an order that is not reserved, for example one that was rejected.
 */
public final class OrderNotReservedException extends RuntimeException {

    public OrderNotReservedException(String id, OrderStatus status) {
        super("order " + id + " cannot be confirmed because it is " + status, null, false, false);
    }
}

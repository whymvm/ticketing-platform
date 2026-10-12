package co.com.ticketing.model.exception;

import co.com.ticketing.model.order.OrderStatus;

/**
 * Thrown when a payment is notified for an order that is not waiting for it, for example an expired one.
 */
public final class OrderNotAwaitingPaymentException extends RuntimeException {

    public OrderNotAwaitingPaymentException(String id, OrderStatus status) {
        super("order " + id + " is not waiting for its payment because it is " + status, null, false, false);
    }
}

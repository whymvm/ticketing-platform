package co.com.ticketing.model.order;

/**
 * Which deadline an order missed: the reservation time or the payment time.
 */
public enum ExpirationReason {
    RESERVATION_TIMEOUT,
    PAYMENT_TIMEOUT
}

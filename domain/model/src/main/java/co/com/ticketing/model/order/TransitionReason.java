package co.com.ticketing.model.order;

/**
 * Why an order moved to its current state.
 */
public enum TransitionReason {
    ORDER_PLACED,
    TICKETS_RESERVED,
    SOLD_OUT,
    EVENT_CLOSED,
    ORDER_CONFIRMED,
    PAYMENT_APPROVED,
    PAYMENT_DECLINED,
    RESERVATION_EXPIRED,
    PAYMENT_TIMEOUT,
    COMPLIMENTARY_ISSUED
}

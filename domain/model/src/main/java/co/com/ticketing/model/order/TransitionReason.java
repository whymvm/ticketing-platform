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
    RESERVATION_EXPIRED
}

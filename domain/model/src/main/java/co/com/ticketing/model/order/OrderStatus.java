package co.com.ticketing.model.order;

/**
 * Name of each order state. The valid transitions are defined by the state types, not here.
 */
public enum OrderStatus {
    QUEUED,
    RESERVED,
    SOLD,
    REJECTED,
    EXPIRED
}

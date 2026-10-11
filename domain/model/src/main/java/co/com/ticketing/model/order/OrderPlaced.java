package co.com.ticketing.model.order;

import java.time.Instant;

/**
 * Domain event: a customer placed an order. It only carries the order id, so whoever reacts to it reads
 * the current order instead of relying on a copy that may be outdated.
 */
public record OrderPlaced(String orderId, Instant occurredAt) {

    /**
     * Creates the event for an order that was just placed.
     */
    public static OrderPlaced from(QueuedOrder order) {
        return new OrderPlaced(order.details().id(), order.details().createdAt());
    }
}

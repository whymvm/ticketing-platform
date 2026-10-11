package co.com.ticketing.model.order;

import java.time.Duration;
import java.time.Instant;

/**
 * An order waiting for its tickets to be reserved.
 */
public record QueuedOrder(OrderDetails details) implements Order {

    /**
     * Places a new order. Its tickets are reserved later.
     */
    public static QueuedOrder create(String id, NewOrder newOrder, String customerId, Instant now) {
        return new QueuedOrder(new OrderDetails(id, newOrder.eventId(), customerId, newOrder.quantity(), now));
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.QUEUED;
    }

    /**
     * Reserves the tickets until now plus the time to live.
     */
    public ReservedOrder reserve(Instant now, Duration ttl) {
        return new ReservedOrder(details, now.plus(ttl));
    }

    /**
     * Rejects the order, for example because there are no tickets left.
     */
    public RejectedOrder reject(RejectionReason rejectionReason) {
        return new RejectedOrder(details, rejectionReason);
    }
}

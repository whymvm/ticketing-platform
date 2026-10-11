package co.com.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

/**
 * One transition of an order: from which state, to which state, why and when. The first entry, when the order
 * is placed, has no previous state.
 */
public record OrderHistoryEntry(String orderId,
                                OrderStatus fromStatus,
                                OrderStatus toStatus,
                                TransitionReason reason,
                                Instant occurredAt) {

    public OrderHistoryEntry {
        Objects.requireNonNull(toStatus, "toStatus is required");
        Objects.requireNonNull(reason, "reason is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    /**
     * Records how the order reached its current state. Each state has a single previous state in this
     * lifecycle, so the entry can be built from the order alone.
     */
    public static OrderHistoryEntry of(Order order) {
        return switch (order) {
            case QueuedOrder queued -> entry(queued, null, TransitionReason.ORDER_PLACED);
            case ReservedOrder reserved -> entry(reserved, OrderStatus.QUEUED, TransitionReason.TICKETS_RESERVED);
            case RejectedOrder rejected -> entry(rejected, OrderStatus.QUEUED, reasonOf(rejected.rejectionReason()));
            case SoldOrder sold -> entry(sold, OrderStatus.RESERVED, TransitionReason.ORDER_CONFIRMED);
            case ExpiredOrder expired -> entry(expired, OrderStatus.RESERVED, TransitionReason.RESERVATION_EXPIRED);
        };
    }

    private static OrderHistoryEntry entry(Order order, OrderStatus fromStatus, TransitionReason reason) {
        return new OrderHistoryEntry(order.details().id(), fromStatus, order.status(), reason, order.updatedAt());
    }

    private static TransitionReason reasonOf(RejectionReason rejectionReason) {
        return switch (rejectionReason) {
            case SOLD_OUT -> TransitionReason.SOLD_OUT;
            case EVENT_CLOSED -> TransitionReason.EVENT_CLOSED;
        };
    }
}

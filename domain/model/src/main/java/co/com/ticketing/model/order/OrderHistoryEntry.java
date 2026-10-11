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
     * Records how the order reached its current state. The state, and for rejections and expirations their reason,
     * tell the previous state, so the entry can be built from the order alone.
     */
    public static OrderHistoryEntry of(Order order) {
        return switch (order) {
            case QueuedOrder queued -> entry(queued, null, TransitionReason.ORDER_PLACED);
            case ReservedOrder reserved -> entry(reserved, OrderStatus.QUEUED, TransitionReason.TICKETS_RESERVED);
            case PendingConfirmationOrder pending ->
                    entry(pending, OrderStatus.RESERVED, TransitionReason.ORDER_CONFIRMED);
            case SoldOrder sold -> entry(sold, OrderStatus.PENDING_CONFIRMATION, TransitionReason.PAYMENT_APPROVED);
            case RejectedOrder rejected -> rejection(rejected);
            case ExpiredOrder expired -> expiration(expired);
        };
    }

    // A payment can only be declined for an order waiting for it; the other rejections happen while it is queued
    private static OrderHistoryEntry rejection(RejectedOrder rejected) {
        return switch (rejected.rejectionReason()) {
            case SOLD_OUT -> entry(rejected, OrderStatus.QUEUED, TransitionReason.SOLD_OUT);
            case EVENT_CLOSED -> entry(rejected, OrderStatus.QUEUED, TransitionReason.EVENT_CLOSED);
            case PAYMENT_DECLINED ->
                    entry(rejected, OrderStatus.PENDING_CONFIRMATION, TransitionReason.PAYMENT_DECLINED);
        };
    }

    private static OrderHistoryEntry expiration(ExpiredOrder expired) {
        return switch (expired.expirationReason()) {
            case RESERVATION_TIMEOUT -> entry(expired, OrderStatus.RESERVED, TransitionReason.RESERVATION_EXPIRED);
            case PAYMENT_TIMEOUT ->
                    entry(expired, OrderStatus.PENDING_CONFIRMATION, TransitionReason.PAYMENT_TIMEOUT);
        };
    }

    private static OrderHistoryEntry entry(Order order, OrderStatus fromStatus, TransitionReason reason) {
        return new OrderHistoryEntry(order.details().id(), fromStatus, order.status(), reason, order.updatedAt());
    }

}

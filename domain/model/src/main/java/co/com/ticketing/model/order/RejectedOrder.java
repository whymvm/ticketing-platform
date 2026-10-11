package co.com.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

/**
 * An order that could not be fulfilled. It is final: it has no transitions.
 */
public record RejectedOrder(OrderDetails details, RejectionReason rejectionReason, Instant updatedAt)
        implements Order {

    public RejectedOrder {
        Objects.requireNonNull(rejectionReason, "rejectionReason is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.REJECTED;
    }
}

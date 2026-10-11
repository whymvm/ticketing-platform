package co.com.ticketing.model.order;

import java.util.Objects;

/**
 * An order that could not be fulfilled. It is final: it has no transitions.
 */
public record RejectedOrder(OrderDetails details, RejectionReason rejectionReason) implements Order {

    public RejectedOrder {
        Objects.requireNonNull(rejectionReason, "rejectionReason is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.REJECTED;
    }
}

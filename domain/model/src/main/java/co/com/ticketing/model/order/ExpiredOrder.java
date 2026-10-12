package co.com.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

/**
 * An order that missed a deadline: it was not confirmed in time, or its payment did not arrive in time. Its
 * tickets went back to the inventory. It is final: it has no transitions.
 */
public record ExpiredOrder(OrderDetails details, ExpirationReason expirationReason, Instant updatedAt)
        implements Order {

    public ExpiredOrder {
        Objects.requireNonNull(expirationReason, "expirationReason is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.EXPIRED;
    }
}

package co.com.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

/**
 * An order whose reservation time ended before it was confirmed. It is final: it has no transitions.
 */
public record ExpiredOrder(OrderDetails details, Instant updatedAt) implements Order {

    public ExpiredOrder {
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.EXPIRED;
    }
}

package co.com.ticketing.model.order;

import java.time.Instant;
import java.util.Objects;

/**
 * An order whose purchase was confirmed. It is final and cannot be undone.
 */
public record SoldOrder(OrderDetails details, Instant updatedAt) implements Order {

    public SoldOrder {
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.SOLD;
    }
}

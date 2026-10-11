package co.com.ticketing.model.order;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An order whose tickets are held until it expires.
 */
public record ReservedOrder(OrderDetails details, Instant expiresAt) implements Order {

    public static final Duration RESERVATION_TIME = Duration.ofMinutes(10);

    public ReservedOrder {
        Objects.requireNonNull(expiresAt, "expiresAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.RESERVED;
    }
}

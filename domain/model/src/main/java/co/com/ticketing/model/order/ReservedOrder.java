package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.ReservationExpiredException;

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

    /**
     * Tells if the reservation time is over at the given instant.
     */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Confirms the purchase, so the reserved tickets become sold. Throws ReservationExpiredException if the
     * reservation time is over, even when its tickets were not released yet.
     */
    public SoldOrder confirm(Instant now) {
        if (isExpiredAt(now)) {
            throw new ReservationExpiredException(details.id());
        }
        return new SoldOrder(details);
    }

    /**
     * Expires the reservation, so its tickets go back to the inventory.
     */
    public ExpiredOrder expire() {
        return new ExpiredOrder(details);
    }
}

package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.ReservationExpiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * An order whose tickets are held until it expires.
 */
public record ReservedOrder(OrderDetails details, Instant expiresAt, Instant updatedAt) implements Order {

    public static final Duration RESERVATION_TIME = Duration.ofMinutes(10);

    public ReservedOrder {
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
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
     * Confirms the purchase and starts the payment, which has its own time limit. Throws
     * ReservationExpiredException if the reservation time is over, even when its tickets were not released yet.
     */
    public PendingConfirmationOrder confirm(Instant now) {
        if (isExpiredAt(now)) {
            throw new ReservationExpiredException(details.id());
        }
        return new PendingConfirmationOrder(details, now.plus(PendingConfirmationOrder.PAYMENT_TIME), now);
    }

    /**
     * Expires the reservation, so its tickets go back to the inventory.
     */
    public ExpiredOrder expire(Instant now) {
        return new ExpiredOrder(details, ExpirationReason.RESERVATION_TIMEOUT, now);
    }
}

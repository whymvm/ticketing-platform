package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.PaymentExpiredException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A confirmed order waiting for its payment. Its tickets are held, but they are not sold yet. If the payment does
 * not arrive in time, the order expires and its tickets go back to the inventory.
 */
public record PendingConfirmationOrder(OrderDetails details, Instant expiresAt, Instant updatedAt) implements Order {

    public static final Duration PAYMENT_TIME = Duration.ofMinutes(5);

    public PendingConfirmationOrder {
        Objects.requireNonNull(expiresAt, "expiresAt is required");
        Objects.requireNonNull(updatedAt, "updatedAt is required");
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.PENDING_CONFIRMATION;
    }

    /**
     * Tells if the payment time is over at the given instant.
     */
    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * The payment was approved, so the held tickets become sold. Throws PaymentExpiredException if the payment
     * time is over, even when the tickets were not released yet: a late approval never sells released tickets.
     */
    public SoldOrder markPaid(Instant now) {
        requireTimeLeft(now);
        return new SoldOrder(details, now);
    }

    /**
     * The payment was declined, so the order is rejected and its tickets go back to the inventory. Throws
     * PaymentExpiredException if the payment time is over: the order expires instead.
     */
    public RejectedOrder declinePayment(Instant now) {
        requireTimeLeft(now);
        return new RejectedOrder(details, RejectionReason.PAYMENT_DECLINED, now);
    }

    /**
     * The payment did not arrive in time, so the order expires and its tickets go back to the inventory.
     */
    public ExpiredOrder expire(Instant now) {
        return new ExpiredOrder(details, ExpirationReason.PAYMENT_TIMEOUT, now);
    }

    private void requireTimeLeft(Instant now) {
        if (isExpiredAt(now)) {
            throw new PaymentExpiredException(details.id());
        }
    }
}

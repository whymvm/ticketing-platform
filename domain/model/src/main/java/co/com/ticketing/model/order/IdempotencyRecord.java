package co.com.ticketing.model.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Remembers which order an idempotency key created, and a fingerprint of the request, for 24 hours.
 */
public record IdempotencyRecord(String key, String requestFingerprint, String orderId, Instant createdAt) {

    public static final Duration LIFETIME = Duration.ofHours(24);

    /**
     * Records that the key created the new order: a queued purchase or a complimentary order.
     */
    public static IdempotencyRecord of(IdempotencyKey key, Order order) {
        OrderDetails details = order.details();
        return new IdempotencyRecord(key.scoped(), fingerprintOf(details, order.status()), details.id(),
                details.createdAt());
    }

    /**
     * Tells if both records come from the same request: same kind of order, customer, event and quantity.
     */
    public boolean sameRequestAs(IdempotencyRecord other) {
        return requestFingerprint.equals(other.requestFingerprint);
    }

    public Instant expiresAt() {
        return createdAt.plus(LIFETIME);
    }

    public boolean isExpiredAt(Instant now) {
        return !now.isBefore(expiresAt());
    }

    // The status of a new order tells a purchase (QUEUED) from a complimentary one, so a key of one never replays
    // an order of the other, even if a customer declares the same id as the organizer
    private static String fingerprintOf(OrderDetails details, OrderStatus newStatus) {
        String request = details.eventId() + "|" + details.customerId() + "|" + details.quantity() + "|" + newStatus;
        return HexFormat.of().formatHex(sha256(request.getBytes(StandardCharsets.UTF_8)));
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("every Java runtime must support SHA-256", e);
        }
    }
}

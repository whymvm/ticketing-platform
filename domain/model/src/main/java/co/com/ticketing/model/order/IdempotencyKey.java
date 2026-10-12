package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.InvalidIdempotencyKeyException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Key that the client sends to make a request safe to repeat. It belongs to a customer, so two customers can use
 * the same key without seeing each other's orders.
 */
public record IdempotencyKey(String customerId, String value) {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 64;
    private static final Pattern FORMAT = Pattern.compile("[A-Za-z0-9_-]{" + MIN_LENGTH + "," + MAX_LENGTH + "}");

    public IdempotencyKey {
        value = Optional.ofNullable(value)
                .filter(key -> !key.isBlank())
                .orElseThrow(MissingIdempotencyKeyException::new);
        value = Optional.of(value)
                .filter(key -> FORMAT.matcher(key).matches())
                .orElseThrow(() -> new InvalidIdempotencyKeyException(MIN_LENGTH, MAX_LENGTH));
    }

    /**
     * The key as it is stored: scoped to the customer.
     */
    public String scoped() {
        return customerId + "#" + value;
    }
}

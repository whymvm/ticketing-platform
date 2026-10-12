package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;

import java.time.Instant;
import java.util.Optional;

/**
 * Data shared by an order in every state. It is validated once, when the order is placed.
 */
public record OrderDetails(String id,
                           String eventId,
                           String customerId,
                           Integer quantity,
                           Instant createdAt) {

    public static final int MAX_TICKETS_PER_ORDER = 6;
    public static final int MAX_CUSTOMER_ID_LENGTH = 64;

    public OrderDetails {
        eventId = validEventId(eventId);
        customerId = validCustomerId(customerId);
        quantity = validQuantity(quantity);
    }

    private static String validEventId(String eventId) {
        return Optional.ofNullable(eventId)
                .filter(value -> !value.isBlank())
                .orElseThrow(InvalidOrderEventException::new);
    }

    private static String validCustomerId(String customerId) {
        return Optional.ofNullable(customerId)
                .filter(value -> !value.isBlank() && value.length() <= MAX_CUSTOMER_ID_LENGTH)
                .orElseThrow(() -> new InvalidCustomerException(MAX_CUSTOMER_ID_LENGTH));
    }

    private static Integer validQuantity(Integer quantity) {
        return Optional.ofNullable(quantity)
                .filter(value -> value >= 1 && value <= MAX_TICKETS_PER_ORDER)
                .orElseThrow(() -> new InvalidOrderQuantityException(quantity, MAX_TICKETS_PER_ORDER));
    }
}

package co.com.ticketing.model.order;

/**
 * Holds the data needed to issue complimentary tickets: who receives them and how many.
 */
public record NewComplimentary(String recipientId,
                               Integer quantity) {
}

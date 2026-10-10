package co.com.ticketing.model.order;

/**
 * Holds the data needed to place an order.
 */
public record NewOrder(String eventId,
                       Integer quantity) {
}

package co.com.ticketing.model.order;

/**
 * Result of placing an order: the order, and whether it was created now or replayed for a repeated request.
 */
public record OrderPlacement(Order order, boolean replayed) {

    public static OrderPlacement created(Order order) {
        return new OrderPlacement(order, false);
    }

    public static OrderPlacement replayed(Order order) {
        return new OrderPlacement(order, true);
    }
}

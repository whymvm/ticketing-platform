package co.com.ticketing.model.order;

/**
 * An order whose reservation time ended before it was confirmed. It is final: it has no transitions.
 */
public record ExpiredOrder(OrderDetails details) implements Order {

    @Override
    public OrderStatus status() {
        return OrderStatus.EXPIRED;
    }
}

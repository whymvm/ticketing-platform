package co.com.ticketing.model.order;

/**
 * An order whose purchase was confirmed. It is final and cannot be undone.
 */
public record SoldOrder(OrderDetails details) implements Order {

    @Override
    public OrderStatus status() {
        return OrderStatus.SOLD;
    }
}

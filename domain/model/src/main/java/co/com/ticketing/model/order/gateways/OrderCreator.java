package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.Order;
import reactor.core.publisher.Mono;

/**
 * Persists new orders.
 */
public interface OrderCreator {

    /**
     * Saves an order already validated by the domain. Never overwrites an existing order.
     *
     * @param order the order to save
     * @return the saved order
     */
    Mono<Order> save(Order order);

}

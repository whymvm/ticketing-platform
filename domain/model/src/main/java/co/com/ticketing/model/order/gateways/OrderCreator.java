package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.QueuedOrder;
import reactor.core.publisher.Mono;

/**
 * Persists new orders.
 */
public interface OrderCreator {

    /**
     * Saves a new order. Never overwrites an existing order.
     *
     * @param order the order to save
     * @return the saved order
     */
    Mono<QueuedOrder> save(QueuedOrder order);

}

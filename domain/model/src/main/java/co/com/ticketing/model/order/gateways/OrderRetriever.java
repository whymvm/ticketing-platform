package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.Order;
import reactor.core.publisher.Mono;

/**
 * Retrieves stored orders.
 */
public interface OrderRetriever {

    /**
     * Finds an order by its id.
     *
     * @param id the order id
     * @return the order in its current state, or an empty Mono if no order has that id
     */
    Mono<Order> findById(String id);

}

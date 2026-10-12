package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.Order;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

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

    /**
     * Finds the orders that missed their deadline: reservations not confirmed in time and payments that did not
     * arrive in time. The result may include a few orders that were processed a moment ago, so the caller must
     * not rely on it alone.
     *
     * @param now the current instant
     * @return the reserved and pending confirmation orders whose deadline is over
     */
    Flux<Order> findOrdersPastDeadline(Instant now);

}

package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.ReservedOrder;
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
     * Finds the reserved orders whose reservation time is over. The result may include a few orders that were
     * processed a moment ago, so the caller must not rely on it alone.
     *
     * @param now the current instant
     * @return the expired reservations
     */
    Flux<ReservedOrder> findExpiredReservations(Instant now);

}

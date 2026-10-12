package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.OrderHistoryEntry;
import reactor.core.publisher.Flux;

/**
 * Retrieves the history of the orders.
 */
public interface OrderHistoryRetriever {

    /**
     * Finds every transition of an order, oldest first.
     *
     * @param orderId the order id
     * @return the transitions of the order, or an empty Flux if it has none
     */
    Flux<OrderHistoryEntry> findByOrderId(String orderId);

}

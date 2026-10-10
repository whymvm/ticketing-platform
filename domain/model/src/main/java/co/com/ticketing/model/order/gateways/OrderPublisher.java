package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.Order;
import reactor.core.publisher.Mono;

/**
 * Queues orders so their tickets are reserved asynchronously.
 */
public interface OrderPublisher {

    /**
     * Publishes an order to be processed.
     *
     * @param order the order to publish
     * @return a Mono that completes once the order is queued
     */
    Mono<Void> publish(Order order);

}

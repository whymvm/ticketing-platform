package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.OrderPlaced;
import reactor.core.publisher.Mono;

/**
 * Publishes order events so other components react to them asynchronously.
 */
public interface OrderPublisher {

    /**
     * Publishes that an order was placed.
     *
     * @param event the event to publish
     * @return a Mono that completes once the event is queued
     */
    Mono<Void> publish(OrderPlaced event);

}

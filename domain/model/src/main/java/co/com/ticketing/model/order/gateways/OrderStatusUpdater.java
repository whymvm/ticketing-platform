package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.RejectedOrder;
import reactor.core.publisher.Mono;

/**
 * Updates the status of stored orders.
 */
public interface OrderStatusUpdater {

    /**
     * Marks a queued order as rejected. Emits OrderAlreadyProcessedException if the order is no longer queued.
     *
     * @param order the rejected order
     * @return the rejected order
     */
    Mono<RejectedOrder> markRejected(RejectedOrder order);

}

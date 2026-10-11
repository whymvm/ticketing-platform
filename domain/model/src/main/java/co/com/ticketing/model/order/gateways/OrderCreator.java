package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.QueuedOrder;
import reactor.core.publisher.Mono;

/**
 * Persists new orders.
 */
public interface OrderCreator {

    /**
     * Saves a new order together with the idempotency record of the request, in one atomic write. Never
     * overwrites an existing order. Emits DuplicateRequestException if another request saved the same key first.
     *
     * @param order  the order to save
     * @param record the idempotency record of the request that placed it
     * @return the saved order
     */
    Mono<QueuedOrder> save(QueuedOrder order, IdempotencyRecord record);

}

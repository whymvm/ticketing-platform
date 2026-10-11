package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.IdempotencyRecord;
import reactor.core.publisher.Mono;

/**
 * Retrieves the idempotency records.
 */
public interface IdempotencyRecordRetriever {

    /**
     * Finds the record of a scoped idempotency key.
     *
     * @param key the key scoped to its customer
     * @return the record, or an empty Mono if the key was never used
     */
    Mono<IdempotencyRecord> findByKey(String key);

}

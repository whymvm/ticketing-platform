package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.IdempotencyRecord;
import reactor.core.publisher.Mono;

/**
 * Issues complimentary tickets.
 */
public interface ComplimentaryIssuer {

    /**
     * Moves the tickets from available to complimentary, saves the order, its history entry and the idempotency
     * record of the request, in one atomic write. Emits TicketsSoldOutException if the event does not have enough
     * available tickets, and DuplicateRequestException if another request with the same key issued them first.
     *
     * @param order  the complimentary order
     * @param record the idempotency record of the request
     * @return the complimentary order
     */
    Mono<ComplimentaryOrder> issue(ComplimentaryOrder order, IdempotencyRecord record);

}

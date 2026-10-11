package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.ReservedOrder;
import reactor.core.publisher.Mono;

/**
 * Updates the ticket inventory of an event.
 */
public interface InventoryUpdater {

    /**
     * Moves the tickets of the order from available to reserved and marks the order as reserved, in one
     * atomic write. Emits TicketsSoldOutException if the event does not have enough available tickets,
     * OrderAlreadyProcessedException if the order is no longer queued, or ConcurrencyConflictException if
     * a concurrent write got in the way.
     *
     * @param order the reserved order
     * @return the reserved order
     */
    Mono<ReservedOrder> reserveTickets(ReservedOrder order);

}

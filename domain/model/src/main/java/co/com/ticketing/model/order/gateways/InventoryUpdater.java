package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
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

    /**
     * Moves the tickets of the order from reserved back to available and marks the order as expired, in one
     * atomic write. Emits OrderAlreadyProcessedException if the order is no longer reserved, or
     * ConcurrencyConflictException if a concurrent write got in the way.
     *
     * @param order the expired order
     * @return the expired order
     */
    Mono<ExpiredOrder> releaseTickets(ExpiredOrder order);

    /**
     * Moves the tickets of the order from reserved to sold and marks the order as sold, in one atomic write.
     * Emits OrderAlreadyProcessedException if the order is no longer reserved, or ConcurrencyConflictException
     * if a concurrent write got in the way.
     *
     * @param order the sold order
     * @return the sold order
     */
    Mono<SoldOrder> sellTickets(SoldOrder order);

}

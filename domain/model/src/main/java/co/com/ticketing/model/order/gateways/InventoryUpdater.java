package co.com.ticketing.model.order.gateways;

import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.RejectedOrder;
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
     * Moves the tickets of the order from reserved to pending confirmation and marks the order as waiting for
     * its payment, in one atomic write. Emits OrderAlreadyProcessedException if the order is no longer
     * reserved, or ConcurrencyConflictException if a concurrent write got in the way.
     *
     * @param order the order waiting for its payment
     * @return the order waiting for its payment
     */
    Mono<PendingConfirmationOrder> holdForPayment(PendingConfirmationOrder order);

    /**
     * Moves the tickets of the order from pending confirmation to sold and marks the order as sold, in one atomic
     * write. Emits OrderAlreadyProcessedException if the order is no longer waiting for its payment, or
     * ConcurrencyConflictException if a concurrent write got in the way.
     *
     * @param order the sold order
     * @return the sold order
     */
    Mono<SoldOrder> sellTickets(SoldOrder order);

    /**
     * Moves the tickets of an expired order back to available, from reserved or from pending confirmation
     * depending on the deadline it missed, and marks the order as expired, in one atomic write. Emits
     * OrderAlreadyProcessedException if the order is no longer in the state it expired from, or
     * ConcurrencyConflictException if a concurrent write got in the way.
     *
     * @param order the expired order
     * @return the expired order
     */
    Mono<ExpiredOrder> releaseTickets(ExpiredOrder order);

    /**
     * Moves the tickets of an order whose payment was declined from pending confirmation back to available and
     * marks the order as rejected, in one atomic write. Emits OrderAlreadyProcessedException if the order is no
     * longer waiting for its payment, or ConcurrencyConflictException if a concurrent write got in the way.
     *
     * @param order the rejected order
     * @return the rejected order
     */
    Mono<RejectedOrder> releaseTickets(RejectedOrder order);

}

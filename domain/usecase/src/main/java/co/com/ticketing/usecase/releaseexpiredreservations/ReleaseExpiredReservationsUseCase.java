package co.com.ticketing.usecase.releaseexpiredreservations;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;

@RequiredArgsConstructor
public class ReleaseExpiredReservationsUseCase {

    static final int MAX_CONCURRENT_RELEASES = 16;

    private final OrderRetriever orderRetriever;
    private final InventoryUpdater inventoryUpdater;
    private final Clock clock;

    /**
     * Expires the reservations whose time is over and puts their tickets back in the inventory.
     * Emits each released order.
     */
    public Flux<ExpiredOrder> run() {
        Instant now = clock.instant();
        return orderRetriever.findExpiredReservations(now)
                .filter(order -> order.isExpiredAt(now))
                .map(ReservedOrder::expire)
                .flatMap(this::release, MAX_CONCURRENT_RELEASES);
    }

    // An order that another process already changed is skipped; a conflict is retried by the next run
    private Mono<ExpiredOrder> release(ExpiredOrder order) {
        return inventoryUpdater.releaseTickets(order)
                .onErrorResume(error -> error instanceof OrderAlreadyProcessedException
                        || error instanceof ConcurrencyConflictException, error -> Mono.empty());
    }
}

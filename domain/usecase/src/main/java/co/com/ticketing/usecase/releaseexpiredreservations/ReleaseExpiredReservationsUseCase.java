package co.com.ticketing.usecase.releaseexpiredreservations;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.Order;
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
     * Expires the reservations and the payments whose time is over and puts their tickets back in the inventory.
     * Emits each released order.
     */
    // Deferred, so each subscription reads the clock again: Spring calls a reactive @Scheduled method only once and
    // subscribes to the same Publisher on every run, so a time read here directly would stay frozen at startup
    public Flux<ExpiredOrder> run() {
        return Flux.defer(() -> {
            Instant now = clock.instant();
            return orderRetriever.findOrdersPastDeadline(now)
                    .flatMap(order -> expire(order, now), MAX_CONCURRENT_RELEASES);
        });
    }

    // The index may return an order that changed a moment ago, so each one is checked again before expiring it
    private Mono<ExpiredOrder> expire(Order order, Instant now) {
        return switch (order) {
            case ReservedOrder reserved when reserved.isExpiredAt(now) -> release(reserved.expire(now));
            case PendingConfirmationOrder pending when pending.isExpiredAt(now) -> release(pending.expire(now));
            case QueuedOrder _, ReservedOrder _, PendingConfirmationOrder _, SoldOrder _, RejectedOrder _,
                 ExpiredOrder _, ComplimentaryOrder _ -> Mono.empty();
        };
    }

    // An order that another process already changed is skipped; a conflict is retried by the next run
    private Mono<ExpiredOrder> release(ExpiredOrder order) {
        return inventoryUpdater.releaseTickets(order)
                .onErrorResume(error -> error instanceof OrderAlreadyProcessedException
                        || error instanceof ConcurrencyConflictException, _ -> Mono.empty());
    }
}

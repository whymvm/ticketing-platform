package co.com.ticketing.usecase.confirmorder;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;

@RequiredArgsConstructor
public class ConfirmOrderUseCase {

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(50);

    private final OrderRetriever orderRetriever;
    private final InventoryUpdater inventoryUpdater;
    private final Clock clock;

    /**
     * Confirms the purchase of a reserved order of the customer, so its tickets become sold. Confirming an
     * order that is already sold returns it again. Emits OrderNotFoundException, ReservationExpiredException
     * or OrderNotReservedException when the order cannot be confirmed, and ConcurrencyConflictException when
     * a concurrent write keeps getting in the way.
     */
    public Mono<SoldOrder> run(String id, String customerId) {
        return findOrder(id, customerId).flatMap(order -> switch (order) {
            case ReservedOrder reserved -> sell(reserved);
            case SoldOrder sold -> Mono.just(sold);
            case QueuedOrder _, RejectedOrder _, ExpiredOrder _ ->
                    Mono.error(new OrderNotReservedException(id, order.status()));
        });
    }

    private Mono<Order> findOrder(String id, String customerId) {
        return orderRetriever.findById(id)
                .filter(order -> order.belongsTo(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)));
    }

    private Mono<SoldOrder> sell(ReservedOrder reserved) {
        return Mono.fromCallable(() -> reserved.confirm(clock.instant()))
                .flatMap(inventoryUpdater::sellTickets)
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .onErrorResume(OrderAlreadyProcessedException.class, error -> currentState(reserved));
    }

    // Another process changed the order first: a repeated confirmation sold it, or the scheduler expired it.
    // If the read still shows it reserved, it is not up to date yet, so the client is asked to try again
    private Mono<SoldOrder> currentState(ReservedOrder reserved) {
        String id = reserved.details().id();
        return orderRetriever.findById(id).flatMap(order -> switch (order) {
            case SoldOrder sold -> Mono.just(sold);
            case ReservedOrder _ -> Mono.error(new ConcurrencyConflictException(id));
            case QueuedOrder _, RejectedOrder _, ExpiredOrder _ ->
                    Mono.error(new OrderNotReservedException(id, order.status()));
        });
    }
}

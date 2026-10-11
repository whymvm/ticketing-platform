package co.com.ticketing.usecase.confirmorder;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.PendingConfirmationOrder;
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
     * Confirms a reserved order of the customer: its tickets wait for the payment, which the payment provider
     * notifies later. Confirming an order that is already waiting for its payment or sold returns it again.
     * Emits OrderNotFoundException, ReservationExpiredException or OrderNotReservedException when the order
     * cannot be confirmed, and ConcurrencyConflictException when a concurrent write keeps getting in the way.
     */
    public Mono<Order> run(String id, String customerId) {
        return findOrder(id, customerId).flatMap(order -> switch (order) {
            case ReservedOrder reserved -> confirm(reserved);
            case PendingConfirmationOrder _, SoldOrder _ -> Mono.just(order);
            case QueuedOrder _, RejectedOrder _, ExpiredOrder _ ->
                    Mono.error(new OrderNotReservedException(id, order.status()));
        });
    }

    private Mono<Order> findOrder(String id, String customerId) {
        return orderRetriever.findById(id)
                .filter(order -> order.belongsTo(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)));
    }

    // If the payment is never notified, the payment time limit releases the tickets (ADR 0008)
    private Mono<Order> confirm(ReservedOrder reserved) {
        return Mono.fromCallable(() -> reserved.confirm(clock.instant()))
                .flatMap(this::holdForPayment)
                .map(Order.class::cast)
                .onErrorResume(OrderAlreadyProcessedException.class, _ -> currentState(reserved));
    }

    private Mono<PendingConfirmationOrder> holdForPayment(PendingConfirmationOrder pending) {
        return Mono.defer(() -> inventoryUpdater.holdForPayment(pending))
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((_, signal) -> signal.failure()));
    }

    // Another process changed the order first: a repeated confirmation moved it on, or the scheduler expired it.
    // If the read still shows it reserved, it is not up to date yet, so the client is asked to try again
    private Mono<Order> currentState(ReservedOrder reserved) {
        String id = reserved.details().id();
        return orderRetriever.findById(id).flatMap(order -> switch (order) {
            case PendingConfirmationOrder _, SoldOrder _ -> Mono.just(order);
            case ReservedOrder _ -> Mono.error(new ConcurrencyConflictException(id));
            case QueuedOrder _, RejectedOrder _, ExpiredOrder _ ->
                    Mono.error(new OrderNotReservedException(id, order.status()));
        });
    }
}

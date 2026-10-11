package co.com.ticketing.usecase.reservetickets;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import co.com.ticketing.model.order.gateways.OrderStatusUpdater;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;

@RequiredArgsConstructor
public class ReserveTicketsUseCase {

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(50);

    private final OrderRetriever orderRetriever;
    private final InventoryUpdater inventoryUpdater;
    private final OrderStatusUpdater orderStatusUpdater;
    private final Clock clock;

    /**
     * Reserves the tickets of a queued order, or rejects it if there are not enough tickets.
     * An order that is no longer queued is ignored, so a repeated message has no effect.
     */
    public Mono<Void> run(String orderId) {
        return orderRetriever.findById(orderId)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(this::process)
                .onErrorResume(OrderAlreadyProcessedException.class, error -> Mono.empty());
    }

    private Mono<Void> process(Order order) {
        return switch (order) {
            case QueuedOrder queued -> reserve(queued);
            case ReservedOrder _, RejectedOrder _ -> Mono.empty();
        };
    }

    private Mono<Void> reserve(QueuedOrder order) {
        return Mono.defer(() -> inventoryUpdater.reserveTickets(
                        order.reserve(clock.instant(), ReservedOrder.RESERVATION_TIME)))
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                .then()
                .onErrorResume(TicketsSoldOutException.class, error -> reject(order));
    }

    private Mono<Void> reject(QueuedOrder order) {
        return orderStatusUpdater.markRejected(order.reject(RejectionReason.SOLD_OUT)).then();
    }
}

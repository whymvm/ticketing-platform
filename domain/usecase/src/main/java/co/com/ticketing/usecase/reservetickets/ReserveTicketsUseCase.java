package co.com.ticketing.usecase.reservetickets;

import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import co.com.ticketing.model.order.gateways.OrderStatusUpdater;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class ReserveTicketsUseCase {

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(50);

    private final OrderRetriever orderRetriever;
    private final EventRetriever eventRetriever;
    private final InventoryUpdater inventoryUpdater;
    private final OrderStatusUpdater orderStatusUpdater;
    private final Clock clock;

    /**
     * Reserves the tickets of a queued order, or rejects it if its event already started or there are not
     * enough tickets. An order that is no longer queued is ignored, so a repeated message has no effect.
     */
    public Mono<Void> run(String orderId) {
        return orderRetriever.findById(orderId)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(this::process)
                .onErrorResume(OrderAlreadyProcessedException.class, _ -> Mono.empty());
    }

    private Mono<Void> process(Order order) {
        return switch (order) {
            case QueuedOrder queued -> reserve(queued);
            case ReservedOrder _, PendingConfirmationOrder _, RejectedOrder _, ExpiredOrder _, SoldOrder _ ->
                    Mono.empty();
        };
    }

    // The queue may be behind, so the event may have started since the order was placed
    private Mono<Void> reserve(QueuedOrder order) {
        String eventId = order.details().eventId();
        Instant now = clock.instant();
        return eventRetriever.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .flatMap(event -> event.isOpenAt(now)
                        ? holdTickets(order, now)
                        : reject(order, RejectionReason.EVENT_CLOSED, now));
    }

    private Mono<Void> holdTickets(QueuedOrder order, Instant now) {
        return Mono.defer(() -> inventoryUpdater.reserveTickets(order.reserve(now, ReservedOrder.RESERVATION_TIME)))
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((_, signal) -> signal.failure()))
                .then()
                .onErrorResume(TicketsSoldOutException.class, _ -> reject(order, RejectionReason.SOLD_OUT, now));
    }

    private Mono<Void> reject(QueuedOrder order, RejectionReason reason, Instant now) {
        return orderStatusUpdater.markRejected(order.reject(reason, now)).then();
    }
}

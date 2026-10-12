package co.com.ticketing.usecase.placeorder;

import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderPlaced;
import co.com.ticketing.model.order.OrderPlacement;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.gateways.IdempotencyRecordRetriever;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class PlaceOrderUseCase {

    private final EventRetriever eventRetriever;
    private final OrderCreator orderCreator;
    private final OrderPublisher orderPublisher;
    private final OrderRetriever orderRetriever;
    private final IdempotencyRecordRetriever idempotencyRecordRetriever;
    private final Clock clock;

    /**
     * Places an order for an event that has not started yet, saves it and publishes OrderPlaced so its tickets
     * are reserved later. A repeated request with the same idempotency key and the same data returns the order
     * it created, in its current state, without creating or publishing anything. Emits an error if the data is
     * invalid, the key was used with other data, the event does not exist or it already started.
     */
    public Mono<OrderPlacement> run(NewOrder newOrder, String customerId, String idempotencyKey) {
        return Mono.fromCallable(() -> Attempt.of(clock.instant(), newOrder, customerId, idempotencyKey))
                .flatMap(attempt -> findRecord(attempt)
                        .flatMap(existing -> replay(existing, attempt.idempotencyRecord()))
                        .switchIfEmpty(Mono.defer(() -> place(attempt))));
    }

    private Mono<OrderPlacement> place(Attempt attempt) {
        return requireOpenEvent(attempt.order())
                .flatMap(order -> orderCreator.save(order, attempt.idempotencyRecord()))
                .flatMap(order -> orderPublisher.publish(OrderPlaced.from(order)).thenReturn(OrderPlacement.created(order)))
                // Another request with the same key won the race: answer with the order it created
                .onErrorResume(DuplicateRequestException.class, _ -> findRecord(attempt)
                        .flatMap(existing -> replay(existing, attempt.idempotencyRecord())));
    }

    // An expired key may still be stored for a while, because DynamoDB deletes expired items late
    private Mono<IdempotencyRecord> findRecord(Attempt attempt) {
        return idempotencyRecordRetriever.findByKey(attempt.idempotencyRecord().key())
                .filter(existing -> !existing.isExpiredAt(attempt.now()));
    }

    private Mono<OrderPlacement> replay(IdempotencyRecord existing, IdempotencyRecord current) {
        if (!existing.sameRequestAs(current)) {
            return Mono.error(new IdempotencyKeyReusedException());
        }
        return orderRetriever.findById(existing.orderId())
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(existing.orderId())))
                .map(OrderPlacement::replayed);
    }

    private Mono<QueuedOrder> requireOpenEvent(QueuedOrder order) {
        OrderDetails details = order.details();
        return eventRetriever.findById(details.eventId())
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(details.eventId())))
                .filter(event -> event.isOpenAt(details.createdAt()))
                .switchIfEmpty(Mono.error(() -> new EventClosedException(details.eventId())))
                .thenReturn(order);
    }

    private record Attempt(Instant now, QueuedOrder order, IdempotencyRecord idempotencyRecord) {

        // Validates the key and the order once, before anything is read or written
        static Attempt of(Instant now, NewOrder newOrder, String customerId, String idempotencyKey) {
            IdempotencyKey key = new IdempotencyKey(customerId, idempotencyKey);
            QueuedOrder order = QueuedOrder.create(UUID.randomUUID().toString(), newOrder, customerId, now);
            return new Attempt(now, order, IdempotencyRecord.of(key, order));
        }
    }
}

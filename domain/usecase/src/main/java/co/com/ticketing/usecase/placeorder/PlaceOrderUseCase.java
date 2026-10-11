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
        return Mono.fromCallable(() -> Request.of(clock.instant(), newOrder, customerId, idempotencyKey))
                .flatMap(request -> findRecord(request)
                        .flatMap(existing -> replay(existing, request.record()))
                        .switchIfEmpty(Mono.defer(() -> place(request))));
    }

    private Mono<OrderPlacement> place(Request request) {
        return requireOpenEvent(request.order())
                .flatMap(order -> orderCreator.save(order, request.record()))
                .flatMap(order -> orderPublisher.publish(OrderPlaced.from(order)).thenReturn(OrderPlacement.created(order)))
                // Another request with the same key won the race: answer with the order it created
                .onErrorResume(DuplicateRequestException.class, _ -> findRecord(request)
                        .flatMap(existing -> replay(existing, request.record())));
    }

    // An expired key may still be stored for a while, because DynamoDB deletes expired items late
    private Mono<IdempotencyRecord> findRecord(Request request) {
        return idempotencyRecordRetriever.findByKey(request.record().key())
                .filter(existing -> !existing.isExpiredAt(request.now()));
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

    private record Request(Instant now, QueuedOrder order, IdempotencyRecord record) {

        // Validates the key and the order once, before anything is read or written
        static Request of(Instant now, NewOrder newOrder, String customerId, String idempotencyKey) {
            IdempotencyKey key = new IdempotencyKey(customerId, idempotencyKey);
            QueuedOrder order = QueuedOrder.create(UUID.randomUUID().toString(), newOrder, customerId, now);
            return new Request(now, order, IdempotencyRecord.of(key, order));
        }
    }
}

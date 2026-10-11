package co.com.ticketing.usecase.issuecomplimentary;

import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.OrderPlacement;
import co.com.ticketing.model.order.gateways.ComplimentaryIssuer;
import co.com.ticketing.model.order.gateways.IdempotencyRecordRetriever;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RequiredArgsConstructor
public class IssueComplimentaryUseCase {

    // Complimentary tickets are issued by the organizer, so their idempotency keys belong to the organizer
    static final String ORGANIZER = "organizer";

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(50);

    private final EventRetriever eventRetriever;
    private final ComplimentaryIssuer complimentaryIssuer;
    private final OrderRetriever orderRetriever;
    private final IdempotencyRecordRetriever idempotencyRecordRetriever;
    private final Clock clock;

    /**
     * Issues complimentary tickets of an event that has not started yet, in one atomic write that takes them from
     * the available tickets. A repeated request with the same idempotency key and the same data returns the order
     * it created. Emits an error if the data is invalid, the key was used with other data, the event does not exist
     * or already started, or there are not enough available tickets, and ConcurrencyConflictException when a
     * concurrent write on the event keeps getting in the way.
     */
    public Mono<OrderPlacement> run(String eventId, NewComplimentary request, String idempotencyKey) {
        return Mono.fromCallable(() -> Request.of(clock.instant(), eventId, request, idempotencyKey))
                .flatMap(issue -> findRecord(issue)
                        .flatMap(existing -> replay(existing, issue.record()))
                        .switchIfEmpty(Mono.defer(() -> issue(issue))));
    }

    private Mono<OrderPlacement> issue(Request request) {
        return requireOpenEvent(request)
                .then(Mono.defer(() -> complimentaryIssuer.issue(request.order(), request.record()))
                        // Reservations update the same event item, so a concurrent one can cancel the transaction.
                        // Nothing was written then, so issuing again is safe
                        .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                                .filter(ConcurrencyConflictException.class::isInstance)
                                .onRetryExhaustedThrow((_, signal) -> signal.failure())))
                .map(OrderPlacement::created)
                // Another request with the same key won the race: answer with the order it created
                .onErrorResume(DuplicateRequestException.class, _ -> findRecord(request)
                        .flatMap(existing -> replay(existing, request.record())));
    }

    private Mono<Void> requireOpenEvent(Request request) {
        String eventId = request.order().details().eventId();
        return eventRetriever.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .filter(event -> event.isOpenAt(request.now()))
                .switchIfEmpty(Mono.error(() -> new EventClosedException(eventId)))
                .then();
    }

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

    private record Request(Instant now, ComplimentaryOrder order, IdempotencyRecord record) {

        // Validates the key and the order once, before anything is read or written
        static Request of(Instant now, String eventId, NewComplimentary request, String idempotencyKey) {
            IdempotencyKey key = new IdempotencyKey(ORGANIZER, idempotencyKey);
            ComplimentaryOrder order = ComplimentaryOrder.issue(UUID.randomUUID().toString(), eventId, request, now);
            return new Request(now, order, IdempotencyRecord.of(key, order));
        }
    }
}

package co.com.ticketing.usecase.recordpayment;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotAwaitingPaymentException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.PaymentNotification;
import co.com.ticketing.model.order.PaymentResult;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@RequiredArgsConstructor
public class RecordPaymentUseCase {

    static final int MAX_RETRIES = 3;
    static final Duration FIRST_RETRY_DELAY = Duration.ofMillis(50);

    private final OrderRetriever orderRetriever;
    private final InventoryUpdater inventoryUpdater;
    private final Clock clock;

    /**
     * Records what the payment provider says about the payment of an order: an approved payment sells its
     * tickets, and a declined one rejects the order and puts its tickets back in the inventory. Payment providers
     * repeat their notifications, so the same notification again returns the order as it is. Emits
     * OrderNotFoundException, PaymentExpiredException or OrderNotAwaitingPaymentException when the payment cannot
     * be recorded.
     */
    public Mono<Order> run(String orderId, PaymentNotification notification) {
        return orderRetriever.findById(orderId)
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                .flatMap(order -> switch (order) {
                    case PendingConfirmationOrder pending -> record(pending, notification.result());
                    case QueuedOrder _, ReservedOrder _, SoldOrder _, RejectedOrder _, ExpiredOrder _,
                         ComplimentaryOrder _ ->
                            alreadyRecorded(order, notification.result());
                });
    }

    // If another process changed the order first (a repeated notification or the scheduler), it is read again
    private Mono<Order> record(PendingConfirmationOrder pending, PaymentResult result) {
        Instant now = clock.instant();
        return Mono.defer(() -> switch (result) {
                    case APPROVED -> inventoryUpdater.sellTickets(pending.markPaid(now)).map(Order.class::cast);
                    case DECLINED -> inventoryUpdater.releaseTickets(pending.declinePayment(now)).map(Order.class::cast);
                })
                .retryWhen(Retry.backoff(MAX_RETRIES, FIRST_RETRY_DELAY)
                        .filter(ConcurrencyConflictException.class::isInstance)
                        .onRetryExhaustedThrow((_, signal) -> signal.failure()))
                .onErrorResume(OrderAlreadyProcessedException.class, _ -> orderRetriever.findById(pending.details().id())
                        .flatMap(current -> current instanceof PendingConfirmationOrder
                                ? Mono.error(new ConcurrencyConflictException(current.details().id()))
                                : alreadyRecorded(current, result)));
    }

    private static Mono<Order> alreadyRecorded(Order order, PaymentResult result) {
        return sameOutcome(order, result)
                ? Mono.just(order)
                : Mono.error(new OrderNotAwaitingPaymentException(order.details().id(), order.status()));
    }

    private static boolean sameOutcome(Order order, PaymentResult result) {
        return (order instanceof SoldOrder && result == PaymentResult.APPROVED)
                || (order instanceof RejectedOrder rejected && result == PaymentResult.DECLINED
                && rejected.rejectionReason() == RejectionReason.PAYMENT_DECLINED);
    }
}

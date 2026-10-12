package co.com.ticketing.usecase.recordpayment;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotAwaitingPaymentException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.PaymentExpiredException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.PaymentNotification;
import co.com.ticketing.model.order.PaymentResult;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordPaymentUseCaseTest {

    private static final Instant CONFIRMED_AT = Instant.parse("2026-10-10T12:05:00Z");
    private static final Instant NOW = CONFIRMED_AT.plus(Duration.ofMinutes(2));
    private static final QueuedOrder QUEUED = new QueuedOrder(
            new OrderDetails("o-1", "e-1", "c-1", 2, Instant.parse("2026-10-10T12:00:00Z")));
    private static final PendingConfirmationOrder PENDING = QUEUED
            .reserve(Instant.parse("2026-10-10T12:00:00Z"), Duration.ofMinutes(10)).confirm(CONFIRMED_AT);
    private static final SoldOrder SOLD = PENDING.markPaid(NOW);
    private static final RejectedOrder DECLINED = PENDING.declinePayment(NOW);
    private static final PaymentNotification APPROVED = new PaymentNotification(PaymentResult.APPROVED);
    private static final PaymentNotification REFUSED = new PaymentNotification(PaymentResult.DECLINED);

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private InventoryUpdater inventoryUpdater;

    private RecordPaymentUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RecordPaymentUseCase(orderRetriever, inventoryUpdater, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void anApprovedPaymentSellsTheTickets() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING));
        when(inventoryUpdater.sellTickets(SOLD)).thenReturn(Mono.just(SOLD));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectNext(SOLD)
                .verifyComplete();
    }

    @Test
    void aDeclinedPaymentRejectsTheOrderAndReleasesItsTickets() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING));
        when(inventoryUpdater.releaseTickets(DECLINED)).thenReturn(Mono.just(DECLINED));

        StepVerifier.create(useCase.run("o-1", REFUSED))
                .expectNext(DECLINED)
                .verifyComplete();
    }

    @Test
    void aRepeatedNotificationReturnsTheOrderAsItIs() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(SOLD), Mono.just(DECLINED));

        StepVerifier.create(useCase.run("o-1", APPROVED)).expectNext(SOLD).verifyComplete();
        StepVerifier.create(useCase.run("o-1", REFUSED)).expectNext(DECLINED).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void refusesANotificationThatContradictsTheRecordedResult() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(SOLD));

        StepVerifier.create(useCase.run("o-1", REFUSED))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotAwaitingPaymentException.class)
                        .hasMessage("order o-1 is not waiting for its payment because it is SOLD"))
                .verify();
    }

    @Test
    void refusesAPaymentForAnOrderThatIsNotWaitingForIt() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED.reject(RejectionReason.SOLD_OUT, NOW)));

        StepVerifier.create(useCase.run("o-1", REFUSED))
                .expectError(OrderNotAwaitingPaymentException.class)
                .verify();
    }

    @Test
    void refusesALatePaymentEvenBeforeTheSchedulerReleasesTheTickets() {
        PendingConfirmationOrder late = QUEUED.reserve(NOW.minus(Duration.ofMinutes(20)), Duration.ofMinutes(10))
                .confirm(NOW.minus(Duration.ofMinutes(15)));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(late));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectError(PaymentExpiredException.class)
                .verify();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void emitsOrderNotFoundWhenTheOrderDoesNotExist() {
        when(orderRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing", APPROVED))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void retriesWhenAConcurrentWriteGetsInTheWay() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING));
        when(inventoryUpdater.sellTickets(SOLD))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")))
                .thenReturn(Mono.just(SOLD));

        StepVerifier.create(useCase.run("o-1", APPROVED)).expectNext(SOLD).verifyComplete();

        verify(inventoryUpdater, times(2)).sellTickets(SOLD);
    }

    @Test
    void givesUpAfterTheLastRetryAndKeepsTheConflict() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING));
        when(inventoryUpdater.sellTickets(any(SoldOrder.class)))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(inventoryUpdater, times(RecordPaymentUseCase.MAX_RETRIES + 1)).sellTickets(SOLD);
    }

    @Test
    void returnsTheSoldOrderWhenARepeatedNotificationWonTheRace() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING), Mono.just(SOLD));
        when(inventoryUpdater.sellTickets(SOLD)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", APPROVED)).expectNext(SOLD).verifyComplete();
    }

    @Test
    void refusesThePaymentWhenTheSchedulerExpiredTheOrderFirst() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING), Mono.just(PENDING.expire(NOW)));
        when(inventoryUpdater.sellTickets(SOLD)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotAwaitingPaymentException.class)
                        .hasMessage("order o-1 is not waiting for its payment because it is EXPIRED"))
                .verify();
    }

    @Test
    void asksToTryAgainWhenTheReadIsNotUpToDateYet() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(PENDING));
        when(inventoryUpdater.sellTickets(SOLD)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }

    @Test
    void refusesAPaymentForComplimentaryTickets() {
        when(orderRetriever.findById("o-1"))
                .thenReturn(Mono.just(ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("c-1", 2), NOW)));

        StepVerifier.create(useCase.run("o-1", APPROVED))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotAwaitingPaymentException.class)
                        .hasMessage("order o-1 is not waiting for its payment because it is COMPLIMENTARY"))
                .verify();

        verifyNoInteractions(inventoryUpdater);
    }
}

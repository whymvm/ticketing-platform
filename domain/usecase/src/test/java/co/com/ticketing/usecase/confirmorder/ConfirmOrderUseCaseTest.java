package co.com.ticketing.usecase.confirmorder;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.exception.ReservationExpiredException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:05:00Z");
    private static final QueuedOrder QUEUED = new QueuedOrder(
            new OrderDetails("o-1", "e-1", "c-1", 2, Instant.parse("2026-10-10T12:00:00Z")));
    private static final ReservedOrder RESERVED = QUEUED.reserve(Instant.parse("2026-10-10T12:00:00Z"),
            Duration.ofMinutes(10));
    private static final PendingConfirmationOrder PENDING = RESERVED.confirm(NOW);

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private InventoryUpdater inventoryUpdater;

    private ConfirmOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ConfirmOrderUseCase(orderRetriever, inventoryUpdater, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void holdsTheTicketsOfAReservedOrderUntilItsPaymentArrives() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED));
        when(inventoryUpdater.holdForPayment(PENDING)).thenReturn(Mono.just(PENDING));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(PENDING)
                .verifyComplete();
    }

    static Stream<Order> ordersAlreadyConfirmed() {
        return Stream.of(PENDING, PENDING.markPaid(NOW.plusSeconds(10)));
    }

    @ParameterizedTest
    @MethodSource("ordersAlreadyConfirmed")
    void returnsTheOrderAgainWhenItIsAlreadyConfirmed(Order order) {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(order)
                .verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    static Stream<Order> ordersThatAreNotReserved() {
        return Stream.of(QUEUED, QUEUED.reject(RejectionReason.SOLD_OUT, NOW), RESERVED.expire(NOW),
                ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("c-1", 2), NOW));
    }

    @ParameterizedTest
    @MethodSource("ordersThatAreNotReserved")
    void rejectsAnOrderThatIsNotReserved(Order order) {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(order));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotReservedException.class)
                        .hasMessage("order o-1 cannot be confirmed because it is " + order.status()))
                .verify();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void rejectsAnExpiredReservationBeforeTheSchedulerReleasesIt() {
        ReservedOrder expired = QUEUED.reserve(NOW.minus(Duration.ofMinutes(11)), Duration.ofMinutes(10));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(expired));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectError(ReservationExpiredException.class)
                .verify();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void hidesTheOrderOfAnotherCustomer() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCase.run("o-1", "someone-else"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void emitsOrderNotFoundWhenTheOrderDoesNotExist() {
        when(orderRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing", "c-1"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void retriesWhenAConcurrentWriteGetsInTheWay() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED));
        when(inventoryUpdater.holdForPayment(PENDING))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")))
                .thenReturn(Mono.just(PENDING));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(PENDING)
                .verifyComplete();

        verify(inventoryUpdater, times(2)).holdForPayment(PENDING);
    }

    @Test
    void givesUpAfterTheLastRetryAndKeepsTheConflict() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED));
        when(inventoryUpdater.holdForPayment(any(PendingConfirmationOrder.class)))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(inventoryUpdater, times(ConfirmOrderUseCase.MAX_RETRIES + 1)).holdForPayment(PENDING);
    }

    @Test
    void returnsTheConfirmedOrderWhenARepeatedConfirmationWonTheRace() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED), Mono.just(PENDING));
        when(inventoryUpdater.holdForPayment(PENDING)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(PENDING)
                .verifyComplete();
    }

    @Test
    void rejectsTheConfirmationWhenTheSchedulerExpiredTheOrderFirst() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED), Mono.just(RESERVED.expire(NOW)));
        when(inventoryUpdater.holdForPayment(PENDING)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotReservedException.class)
                        .hasMessage("order o-1 cannot be confirmed because it is EXPIRED"))
                .verify();
    }

    @Test
    void asksToTryAgainWhenTheReadIsNotUpToDateYet() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED));
        when(inventoryUpdater.holdForPayment(PENDING)).thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }
}

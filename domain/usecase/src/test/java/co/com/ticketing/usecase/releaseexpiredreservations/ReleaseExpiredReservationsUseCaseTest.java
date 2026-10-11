package co.com.ticketing.usecase.releaseexpiredreservations;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.ExpirationReason;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReleaseExpiredReservationsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:30:00Z");
    private static final ReservedOrder EXPIRED_RESERVATION = reserved("o-1", NOW.minus(Duration.ofMinutes(15)));
    private static final ReservedOrder LIVE_RESERVATION = reserved("o-2", NOW.minus(Duration.ofMinutes(5)));

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private InventoryUpdater inventoryUpdater;

    private ReleaseExpiredReservationsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReleaseExpiredReservationsUseCase(orderRetriever, inventoryUpdater,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void releasesTheTicketsOfEachExpiredReservation() {
        ExpiredOrder expired = EXPIRED_RESERVATION.expire(NOW);
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(EXPIRED_RESERVATION));
        when(inventoryUpdater.releaseTickets(expired)).thenReturn(Mono.just(expired));

        StepVerifier.create(useCase.run())
                .expectNext(expired)
                .verifyComplete();
    }

    @Test
    void releasesThePaymentHoldOfAnOrderWhosePaymentDidNotArriveInTime() {
        PendingConfirmationOrder pending = reserved("o-9", NOW.minus(Duration.ofMinutes(8)))
                .confirm(NOW.minus(Duration.ofMinutes(6)));
        ExpiredOrder expired = pending.expire(NOW);
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(pending));
        when(inventoryUpdater.releaseTickets(expired)).thenReturn(Mono.just(expired));

        StepVerifier.create(useCase.run())
                .assertNext(order -> assertThat(order.expirationReason()).isEqualTo(ExpirationReason.PAYMENT_TIMEOUT))
                .verifyComplete();
    }

    @Test
    void skipsAPaymentHoldThatIsNotExpiredYet() {
        PendingConfirmationOrder pending = reserved("o-9", NOW.minus(Duration.ofMinutes(3)))
                .confirm(NOW.minus(Duration.ofMinutes(2)));
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(pending));

        StepVerifier.create(useCase.run()).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void skipsComplimentaryTicketsBecauseTheyNeverExpire() {
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(
                ComplimentaryOrder.issue("o-3", "e-1", new NewComplimentary("guest-001", 2), NOW.minus(Duration.ofDays(1)))));

        StepVerifier.create(useCase.run()).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void skipsAnOrderThatNoLongerHoldsTickets() {
        when(orderRetriever.findOrdersPastDeadline(NOW))
                .thenReturn(Flux.just(EXPIRED_RESERVATION.expire(NOW.minusSeconds(5))));

        StepVerifier.create(useCase.run()).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    // Spring subscribes to the same Publisher on every scheduled run, so each subscription must read the clock again
    @Test
    void readsTheClockAgainOnEverySubscription() {
        Instant later = NOW.plus(Duration.ofMinutes(1));
        Clock advancing = mock(Clock.class);
        when(advancing.instant()).thenReturn(NOW, later);
        when(orderRetriever.findOrdersPastDeadline(any(Instant.class))).thenReturn(Flux.empty());
        Flux<ExpiredOrder> run = new ReleaseExpiredReservationsUseCase(orderRetriever, inventoryUpdater, advancing).run();

        StepVerifier.create(run).verifyComplete();
        StepVerifier.create(run).verifyComplete();

        verify(orderRetriever).findOrdersPastDeadline(NOW);
        verify(orderRetriever).findOrdersPastDeadline(later);
    }

    @Test
    void doesNotReadTheClockUntilSubscribed() {
        Clock clock = mock(Clock.class);

        new ReleaseExpiredReservationsUseCase(orderRetriever, inventoryUpdater, clock).run();

        verifyNoInteractions(clock, orderRetriever);
    }

    @Test
    void skipsAReservationThatIsNotExpiredYet() {
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(LIVE_RESERVATION));

        StepVerifier.create(useCase.run()).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void skipsOrdersThatAnotherProcessChangedAndKeepsGoing() {
        ReservedOrder other = reserved("o-3", NOW.minus(Duration.ofMinutes(20)));
        ReservedOrder conflicted = reserved("o-4", NOW.minus(Duration.ofMinutes(20)));
        when(orderRetriever.findOrdersPastDeadline(NOW))
                .thenReturn(Flux.just(EXPIRED_RESERVATION, other, conflicted));
        when(inventoryUpdater.releaseTickets(any(ExpiredOrder.class))).thenAnswer(invocation -> {
            ExpiredOrder order = invocation.getArgument(0);
            return switch (order.details().id()) {
                case "o-1" -> Mono.error(new OrderAlreadyProcessedException("o-1"));
                case "o-4" -> Mono.error(new ConcurrencyConflictException("o-4"));
                default -> Mono.just(order);
            };
        });

        StepVerifier.create(useCase.run())
                .expectNext(other.expire(NOW))
                .verifyComplete();
    }

    @Test
    void propagatesOtherErrors() {
        when(orderRetriever.findOrdersPastDeadline(NOW)).thenReturn(Flux.just(EXPIRED_RESERVATION));
        when(inventoryUpdater.releaseTickets(any(ExpiredOrder.class)))
                .thenReturn(Mono.error(new IllegalStateException("dynamo down")));

        StepVerifier.create(useCase.run())
                .expectErrorMessage("dynamo down")
                .verify();

        verify(inventoryUpdater).releaseTickets(EXPIRED_RESERVATION.expire(NOW));
    }

    private static ReservedOrder reserved(String id, Instant reservedAt) {
        return new QueuedOrder(new OrderDetails(id, "e-1", "c-1", 2, reservedAt))
                .reserve(reservedAt, ReservedOrder.RESERVATION_TIME);
    }
}

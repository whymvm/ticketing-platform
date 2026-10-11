package co.com.ticketing.usecase.releaseexpiredreservations;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.OrderDetails;
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

import static org.mockito.ArgumentMatchers.any;
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
        when(orderRetriever.findExpiredReservations(NOW)).thenReturn(Flux.just(EXPIRED_RESERVATION));
        when(inventoryUpdater.releaseTickets(expired)).thenReturn(Mono.just(expired));

        StepVerifier.create(useCase.run())
                .expectNext(expired)
                .verifyComplete();
    }

    @Test
    void skipsAReservationThatIsNotExpiredYet() {
        when(orderRetriever.findExpiredReservations(NOW)).thenReturn(Flux.just(LIVE_RESERVATION));

        StepVerifier.create(useCase.run()).verifyComplete();

        verifyNoInteractions(inventoryUpdater);
    }

    @Test
    void skipsOrdersThatAnotherProcessChangedAndKeepsGoing() {
        ReservedOrder other = reserved("o-3", NOW.minus(Duration.ofMinutes(20)));
        ReservedOrder conflicted = reserved("o-4", NOW.minus(Duration.ofMinutes(20)));
        when(orderRetriever.findExpiredReservations(NOW))
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
        when(orderRetriever.findExpiredReservations(NOW)).thenReturn(Flux.just(EXPIRED_RESERVATION));
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

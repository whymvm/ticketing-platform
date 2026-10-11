package co.com.ticketing.usecase.reservetickets;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import co.com.ticketing.model.order.gateways.OrderStatusUpdater;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReserveTicketsUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final QueuedOrder QUEUED = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW));
    private static final ReservedOrder RESERVED = QUEUED.reserve(NOW, Duration.ofMinutes(10));
    private static final RejectedOrder REJECTED = QUEUED.reject(RejectionReason.SOLD_OUT);

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private InventoryUpdater inventoryUpdater;

    @Mock
    private OrderStatusUpdater orderStatusUpdater;

    private ReserveTicketsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ReserveTicketsUseCase(orderRetriever, inventoryUpdater, orderStatusUpdater,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void reservesTheTicketsOfAQueuedOrderForTenMinutes() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED)).thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();

        verify(inventoryUpdater).reserveTickets(RESERVED);
        verifyNoInteractions(orderStatusUpdater);
    }

    @Test
    void rejectsTheOrderWhenTheTicketsAreSoldOut() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED)).thenReturn(Mono.error(new TicketsSoldOutException("e-1", 2)));
        when(orderStatusUpdater.markRejected(REJECTED)).thenReturn(Mono.just(REJECTED));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();

        verify(orderStatusUpdater).markRejected(REJECTED);
    }

    @Test
    void ignoresAnOrderThatIsNoLongerQueued() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(RESERVED), Mono.just(REJECTED),
                Mono.just(RESERVED.expire()));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();
        StepVerifier.create(useCase.run("o-1")).verifyComplete();
        StepVerifier.create(useCase.run("o-1")).verifyComplete();

        verifyNoInteractions(inventoryUpdater, orderStatusUpdater);
    }

    @Test
    void ignoresAnOrderReservedByAnotherProcessAtTheSameTime() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED))
                .thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();

        verifyNoInteractions(orderStatusUpdater);
    }

    @Test
    void ignoresAnOrderRejectedByAnotherProcessAtTheSameTime() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED)).thenReturn(Mono.error(new TicketsSoldOutException("e-1", 2)));
        when(orderStatusUpdater.markRejected(REJECTED))
                .thenReturn(Mono.error(new OrderAlreadyProcessedException("o-1")));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();
    }

    @Test
    void emitsOrderNotFoundWhenTheOrderDoesNotExist() {
        when(orderRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void retriesWhenAConcurrentWriteGetsInTheWay() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")))
                .thenReturn(Mono.just(RESERVED));

        StepVerifier.create(useCase.run("o-1")).verifyComplete();

        verify(inventoryUpdater, times(2)).reserveTickets(RESERVED);
    }

    @Test
    void givesUpAfterTheLastRetryAndKeepsTheConflict() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")));

        StepVerifier.create(useCase.run("o-1"))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(inventoryUpdater, times(ReserveTicketsUseCase.MAX_RETRIES + 1)).reserveTickets(RESERVED);
        verify(orderStatusUpdater, never()).markRejected(any());
    }

    @Test
    void doesNotRetryOtherErrors() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(QUEUED));
        when(inventoryUpdater.reserveTickets(RESERVED)).thenReturn(Mono.error(new IllegalStateException("dynamo down")));

        StepVerifier.create(useCase.run("o-1"))
                .expectErrorMessage("dynamo down")
                .verify();

        verify(inventoryUpdater, times(1)).reserveTickets(RESERVED);
    }
}

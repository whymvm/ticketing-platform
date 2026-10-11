package co.com.ticketing.usecase.retrieveorderhistory;

import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.gateways.OrderHistoryRetriever;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrieveOrderHistoryUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final QueuedOrder ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW));
    private static final OrderHistoryEntry PLACED = OrderHistoryEntry.of(ORDER);
    private static final OrderHistoryEntry RESERVED = OrderHistoryEntry.of(ORDER.reserve(NOW, Duration.ofMinutes(10)));

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private OrderHistoryRetriever orderHistoryRetriever;

    private RetrieveOrderHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetrieveOrderHistoryUseCase(orderRetriever, orderHistoryRetriever);
    }

    @Test
    void returnsTheTransitionsOfTheOrderOfTheCustomer() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(ORDER));
        when(orderHistoryRetriever.findByOrderId("o-1")).thenReturn(Flux.just(PLACED, RESERVED));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(PLACED, RESERVED)
                .verifyComplete();
    }

    @Test
    void hidesTheHistoryOfAnotherCustomer() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(ORDER));

        StepVerifier.create(useCase.run("o-1", "someone-else"))
                .expectError(OrderNotFoundException.class)
                .verify();

        verifyNoInteractions(orderHistoryRetriever);
    }

    @Test
    void emitsOrderNotFoundWhenTheOrderDoesNotExist() {
        when(orderRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing", "c-1"))
                .expectError(OrderNotFoundException.class)
                .verify();

        verifyNoInteractions(orderHistoryRetriever);
    }
}

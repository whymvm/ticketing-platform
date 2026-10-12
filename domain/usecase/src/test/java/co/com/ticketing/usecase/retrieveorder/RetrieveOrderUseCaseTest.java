package co.com.ticketing.usecase.retrieveorder;

import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrieveOrderUseCaseTest {

    private static final QueuedOrder ORDER = new QueuedOrder(
            new OrderDetails("o-1", "e-1", "c-1", 2, Instant.parse("2026-10-10T12:00:00Z")));

    @Mock
    private OrderRetriever orderRetriever;

    private RetrieveOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetrieveOrderUseCase(orderRetriever);
    }

    @Test
    void returnsTheOrderInItsCurrentState() {
        var rejected = ORDER.reject(RejectionReason.SOLD_OUT, Instant.parse("2026-10-10T12:05:00Z"));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(rejected));

        StepVerifier.create(useCase.run("o-1", "c-1"))
                .expectNext(rejected)
                .verifyComplete();
    }

    @Test
    void emitsOrderNotFoundWhenNoOrderHasTheId() {
        when(orderRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing", "c-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderNotFoundException.class)
                        .hasMessage("no order found with id missing"))
                .verify();
    }

    @Test
    void hidesTheOrderOfAnotherCustomer() {
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(ORDER));

        StepVerifier.create(useCase.run("o-1", "someone-else"))
                .expectError(OrderNotFoundException.class)
                .verify();
    }
}

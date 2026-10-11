package co.com.ticketing.usecase.placeorder;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderPlaced;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Event EVENT = new Event("e-1", "Concert", "Arena",
            Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000,
            Inventory.initial(5000), NOW);

    @Mock
    private EventRetriever eventRetriever;

    @Mock
    private OrderCreator orderCreator;

    @Mock
    private OrderPublisher orderPublisher;

    private PlaceOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlaceOrderUseCase(eventRetriever, orderCreator, orderPublisher,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesTheOrderAndPublishesOrderPlaced() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(new NewOrder("e-1", 2), "c-1"))
                .assertNext(order -> {
                    OrderDetails details = order.details();
                    assertThat(details.id()).isNotBlank();
                    assertThat(details.eventId()).isEqualTo("e-1");
                    assertThat(details.customerId()).isEqualTo("c-1");
                    assertThat(details.quantity()).isEqualTo(2);
                    assertThat(details.createdAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        ArgumentCaptor<OrderPlaced> published = ArgumentCaptor.forClass(OrderPlaced.class);
        verify(orderPublisher).publish(published.capture());
        assertThat(published.getValue().orderId()).isNotBlank();
        assertThat(published.getValue().occurredAt()).isEqualTo(NOW);
    }

    @Test
    void rejectsAnInvalidOrderWithoutTouchingTheGateways() {
        StepVerifier.create(useCase.run(new NewOrder("e-1", 0), "c-1"))
                .expectError(InvalidOrderQuantityException.class)
                .verify();

        verifyNoInteractions(eventRetriever, orderCreator, orderPublisher);
    }

    @Test
    void emitsEventNotFoundAndDoesNotSaveTheOrder() {
        when(eventRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(new NewOrder("missing", 1), "c-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventNotFoundException.class)
                        .hasMessage("no event found with id missing"))
                .verify();

        verifyNoInteractions(orderCreator, orderPublisher);
    }

    @Test
    void emitsEventClosedAndDoesNotSaveTheOrderWhenTheEventAlreadyStarted() {
        Event started = new Event("e-1", "Concert", "Arena", NOW.minusSeconds(60), NOW.plusSeconds(3600), 5000,
                Inventory.initial(5000), NOW.minusSeconds(86400));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(started));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventClosedException.class)
                        .hasMessage("event e-1 already started, so its sales are closed"))
                .verify();

        verifyNoInteractions(orderCreator, orderPublisher);
    }

    @Test
    void emitsEventClosedWhenTheEventStartsAtThisVeryInstant() {
        Event startingNow = new Event("e-1", "Concert", "Arena", NOW, NOW.plusSeconds(3600), 5000,
                Inventory.initial(5000), NOW.minusSeconds(86400));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(startingNow));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1"))
                .expectError(EventClosedException.class)
                .verify();
    }

    @Test
    void doesNotPublishWhenTheOrderCannotBeSaved() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class))).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1"))
                .expectErrorMessage("db down")
                .verify();

        verify(orderPublisher, never()).publish(any());
    }

    @Test
    void propagatesPublishingErrors() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class)))
                .thenReturn(Mono.error(new IllegalStateException("queue down")));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1"))
                .expectErrorMessage("queue down")
                .verify();
    }
}

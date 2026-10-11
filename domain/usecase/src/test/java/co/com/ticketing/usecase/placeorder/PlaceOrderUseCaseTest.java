package co.com.ticketing.usecase.placeorder;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.InvalidIdempotencyKeyException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderPlaced;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.IdempotencyRecordRetriever;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlaceOrderUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final String KEY = "key-0001";
    private static final String SCOPED_KEY = "c-1#" + KEY;
    private static final NewOrder NEW_ORDER = new NewOrder("e-1", 2);
    private static final Event EVENT = new Event("e-1", "Concert", "Arena",
            Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000,
            Inventory.initial(5000), NOW);

    @Mock
    private EventRetriever eventRetriever;

    @Mock
    private OrderCreator orderCreator;

    @Mock
    private OrderPublisher orderPublisher;

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private IdempotencyRecordRetriever idempotencyRecordRetriever;

    private PlaceOrderUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new PlaceOrderUseCase(eventRetriever, orderCreator, orderPublisher, orderRetriever,
                idempotencyRecordRetriever, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesTheOrderWithItsKeyAndPublishesOrderPlaced() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isFalse();
                    OrderDetails details = placement.order().details();
                    assertThat(details.eventId()).isEqualTo("e-1");
                    assertThat(details.customerId()).isEqualTo("c-1");
                    assertThat(details.quantity()).isEqualTo(2);
                    assertThat(details.createdAt()).isEqualTo(NOW);
                })
                .verifyComplete();

        ArgumentCaptor<QueuedOrder> order = ArgumentCaptor.forClass(QueuedOrder.class);
        ArgumentCaptor<IdempotencyRecord> savedRecord = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(orderCreator).save(order.capture(), savedRecord.capture());
        assertThat(savedRecord.getValue().key()).isEqualTo(SCOPED_KEY);
        assertThat(savedRecord.getValue().orderId()).isEqualTo(order.getValue().details().id());
        verify(orderPublisher).publish(OrderPlaced.from(order.getValue()));
    }

    @Test
    void replaysTheOriginalOrderInItsCurrentStateForARetryOfTheSameRequest() {
        QueuedOrder original = order("o-1");
        ReservedOrder current = original.reserve(NOW.plusSeconds(1), Duration.ofMinutes(10));
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(original)));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(current));

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isTrue();
                    assertThat(placement.order()).isEqualTo(current);
                })
                .verifyComplete();

        verifyNoInteractions(eventRetriever, orderCreator, orderPublisher);
    }

    @Test
    void rejectsAKeyThatWasUsedWithAnotherRequest() {
        QueuedOrder original = QueuedOrder.create("o-1", new NewOrder("e-1", 5), "c-1", NOW);
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(original)));

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IdempotencyKeyReusedException.class)
                        .hasMessage("the Idempotency-Key was already used with a different request"))
                .verify();

        verifyNoInteractions(eventRetriever, orderCreator, orderPublisher, orderRetriever);
    }

    @Test
    void placesANewOrderWhenTheStoredKeyAlreadyExpired() {
        QueuedOrder old = QueuedOrder.create("o-old", NEW_ORDER, "c-1", NOW.minus(Duration.ofHours(25)));
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(old)));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isFalse();
                    assertThat(placement.order().details().id()).isNotEqualTo("o-old");
                })
                .verifyComplete();
    }

    @Test
    void answersWithTheWinningOrderWhenAConcurrentRequestWithTheSameKeySavedFirst() {
        QueuedOrder winner = order("o-winner");
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY))
                .thenReturn(Mono.empty(), Mono.just(recordOf(winner)));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenReturn(Mono.error(new DuplicateRequestException(SCOPED_KEY)));
        when(orderRetriever.findById("o-winner")).thenReturn(Mono.just(winner));

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isTrue();
                    assertThat(placement.order()).isEqualTo(winner);
                })
                .verifyComplete();

        verify(orderPublisher, never()).publish(any());
    }

    @Test
    void failsWhenAKeyPointsToAnOrderThatDoesNotExist() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(order("o-1"))));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", KEY))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void theKeyBelongsToTheCustomer() {
        when(idempotencyRecordRetriever.findByKey("c-2#" + KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class))).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(NEW_ORDER, "c-2", KEY))
                .assertNext(placement -> assertThat(placement.order().details().customerId()).isEqualTo("c-2"))
                .verifyComplete();
    }

    @Test
    void requiresAnIdempotencyKey() {
        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", null))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(MissingIdempotencyKeyException.class)
                        .hasMessage("the Idempotency-Key header is required"))
                .verify();

        verifyNoInteractions(idempotencyRecordRetriever, eventRetriever, orderCreator, orderPublisher);
    }

    @Test
    void rejectsAKeyWithAnInvalidFormat() {
        StepVerifier.create(useCase.run(NEW_ORDER, "c-1", "bad key!"))
                .expectError(InvalidIdempotencyKeyException.class)
                .verify();

        verifyNoInteractions(idempotencyRecordRetriever, eventRetriever, orderCreator, orderPublisher);
    }

    @Test
    void rejectsAnInvalidOrderWithoutTouchingTheGateways() {
        StepVerifier.create(useCase.run(new NewOrder("e-1", 0), "c-1", KEY))
                .expectError(InvalidOrderQuantityException.class)
                .verify();

        verifyNoInteractions(idempotencyRecordRetriever, eventRetriever, orderCreator, orderPublisher);
    }

    @Test
    void emitsEventNotFoundAndDoesNotSaveTheOrder() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run(new NewOrder("missing", 1), "c-1", KEY))
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
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(started));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1", KEY))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventClosedException.class)
                        .hasMessage("event e-1 already started, so its sales are closed"))
                .verify();

        verifyNoInteractions(orderCreator, orderPublisher);
    }

    @Test
    void doesNotPublishWhenTheOrderCannotBeSaved() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1", KEY))
                .expectErrorMessage("db down")
                .verify();

        verify(orderPublisher, never()).publish(any());
    }

    @Test
    void propagatesPublishingErrors() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(orderCreator.save(any(QueuedOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        when(orderPublisher.publish(any(OrderPlaced.class)))
                .thenReturn(Mono.error(new IllegalStateException("queue down")));

        StepVerifier.create(useCase.run(new NewOrder("e-1", 1), "c-1", KEY))
                .expectErrorMessage("queue down")
                .verify();
    }

    private static QueuedOrder order(String id) {
        return QueuedOrder.create(id, NEW_ORDER, "c-1", NOW);
    }

    private static IdempotencyRecord recordOf(QueuedOrder order) {
        return IdempotencyRecord.of(new IdempotencyKey("c-1", KEY), order);
    }
}

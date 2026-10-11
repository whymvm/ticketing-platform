package co.com.ticketing.usecase.issuecomplimentary;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.gateways.ComplimentaryIssuer;
import co.com.ticketing.model.order.gateways.IdempotencyRecordRetriever;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IssueComplimentaryUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final String KEY = "comp-0001";
    private static final String SCOPED_KEY = "organizer#" + KEY;
    private static final NewComplimentary REQUEST = new NewComplimentary("guest-001", 2);
    private static final Event EVENT = new Event("e-1", "Concert", "Arena", NOW.plusSeconds(86400),
            NOW.plusSeconds(90000), 10, Inventory.initial(10), NOW);

    @Mock
    private EventRetriever eventRetriever;

    @Mock
    private ComplimentaryIssuer complimentaryIssuer;

    @Mock
    private OrderRetriever orderRetriever;

    @Mock
    private IdempotencyRecordRetriever idempotencyRecordRetriever;

    private IssueComplimentaryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new IssueComplimentaryUseCase(eventRetriever, complimentaryIssuer, orderRetriever,
                idempotencyRecordRetriever, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void issuesTheTicketsWithTheKeyOfTheOrganizer() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isFalse();
                    assertThat(placement.order()).isInstanceOf(ComplimentaryOrder.class);
                    assertThat(placement.order().details().customerId()).isEqualTo("guest-001");
                    assertThat(placement.order().details().quantity()).isEqualTo(2);
                })
                .verifyComplete();

        ArgumentCaptor<IdempotencyRecord> savedRecord = ArgumentCaptor.forClass(IdempotencyRecord.class);
        verify(complimentaryIssuer).issue(any(ComplimentaryOrder.class), savedRecord.capture());
        assertThat(savedRecord.getValue().key()).isEqualTo(SCOPED_KEY);
    }

    @Test
    void replaysTheTicketsIssuedForARetryOfTheSameRequest() {
        ComplimentaryOrder original = ComplimentaryOrder.issue("o-1", "e-1", REQUEST, NOW);
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(original)));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.just(original));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isTrue();
                    assertThat(placement.order()).isEqualTo(original);
                })
                .verifyComplete();

        verifyNoInteractions(eventRetriever, complimentaryIssuer);
    }

    @Test
    void issuesNewTicketsWhenTheRecordOfTheKeyAlreadyExpired() {
        ComplimentaryOrder old = ComplimentaryOrder.issue("o-old", "e-1", REQUEST, NOW.minus(Duration.ofDays(2)));
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(old)));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .assertNext(placement -> {
                    assertThat(placement.replayed()).isFalse();
                    assertThat(placement.order().details().id()).isNotEqualTo("o-old");
                })
                .verifyComplete();

        verifyNoInteractions(orderRetriever);
    }

    @Test
    void rejectsAKeyThatWasUsedForAnotherRecipient() {
        ComplimentaryOrder original = ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("guest-002", 2), NOW);
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.just(recordOf(original)));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .expectError(IdempotencyKeyReusedException.class)
                .verify();

        verifyNoInteractions(complimentaryIssuer);
    }

    @Test
    void answersWithTheWinningOrderWhenAConcurrentRequestWithTheSameKeyIssuedFirst() {
        ComplimentaryOrder winner = ComplimentaryOrder.issue("o-winner", "e-1", REQUEST, NOW);
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty(), Mono.just(recordOf(winner)));
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenReturn(Mono.error(new DuplicateRequestException(SCOPED_KEY)));
        when(orderRetriever.findById("o-winner")).thenReturn(Mono.just(winner));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .assertNext(placement -> assertThat(placement.order()).isEqualTo(winner))
                .verifyComplete();
    }

    @Test
    void issuesAgainWhenAConcurrentWriteOnTheEventCancelledTheTransaction() {
        AtomicInteger attempts = new AtomicInteger();
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenAnswer(invocation -> attempts.incrementAndGet() == 1
                        ? Mono.error(new ConcurrencyConflictException("o-1"))
                        : Mono.just(invocation.getArgument(0)));

        StepVerifier.withVirtualTime(() -> useCase.run("e-1", REQUEST, KEY))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(placement -> assertThat(placement.replayed()).isFalse())
                .verifyComplete();

        verify(complimentaryIssuer, times(2)).issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class));
    }

    @Test
    void givesUpAfterTheLastRetryWithTheConflict() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenReturn(Mono.error(new ConcurrencyConflictException("o-1")));

        StepVerifier.withVirtualTime(() -> useCase.run("e-1", REQUEST, KEY))
                .thenAwait(Duration.ofSeconds(5))
                .expectError(ConcurrencyConflictException.class)
                .verify();

        verify(complimentaryIssuer, times(IssueComplimentaryUseCase.MAX_RETRIES + 1))
                .issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class));
    }

    @Test
    void propagatesThatTheTicketsAreSoldOut() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));
        when(complimentaryIssuer.issue(any(ComplimentaryOrder.class), any(IdempotencyRecord.class)))
                .thenReturn(Mono.error(new TicketsSoldOutException("e-1", 2)));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .expectError(TicketsSoldOutException.class)
                .verify();
    }

    @Test
    void emitsEventNotFoundWithoutIssuingAnything() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-9")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("e-9", REQUEST, KEY))
                .expectError(EventNotFoundException.class)
                .verify();

        verifyNoInteractions(complimentaryIssuer);
    }

    @Test
    void emitsEventClosedWhenTheEventAlreadyStarted() {
        Event started = new Event("e-1", "Concert", "Arena", NOW.minusSeconds(60), NOW.plusSeconds(3600), 10,
                Inventory.initial(10), NOW.minusSeconds(86400));
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY)).thenReturn(Mono.empty());
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(started));

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .expectError(EventClosedException.class)
                .verify();

        verifyNoInteractions(complimentaryIssuer);
    }

    @Test
    void failsWhenAKeyPointsToAnOrderThatDoesNotExist() {
        when(idempotencyRecordRetriever.findByKey(SCOPED_KEY))
                .thenReturn(Mono.just(recordOf(ComplimentaryOrder.issue("o-1", "e-1", REQUEST, NOW))));
        when(orderRetriever.findById("o-1")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("e-1", REQUEST, KEY))
                .expectError(OrderNotFoundException.class)
                .verify();
    }

    @Test
    void validatesTheKeyAndTheRequestBeforeReadingAnything() {
        StepVerifier.create(useCase.run("e-1", REQUEST, null))
                .expectError(MissingIdempotencyKeyException.class)
                .verify();
        StepVerifier.create(useCase.run("e-1", new NewComplimentary("guest-001", 0), KEY))
                .expectError(InvalidOrderQuantityException.class)
                .verify();

        verifyNoInteractions(idempotencyRecordRetriever, eventRetriever, complimentaryIssuer, orderRetriever);
    }

    private static IdempotencyRecord recordOf(ComplimentaryOrder order) {
        return IdempotencyRecord.of(new IdempotencyKey("organizer", KEY), order);
    }
}

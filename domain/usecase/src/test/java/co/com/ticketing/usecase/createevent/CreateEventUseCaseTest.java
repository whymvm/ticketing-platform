package co.com.ticketing.usecase.createevent;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.model.event.gateways.EventCreator;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateEventUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Instant STARTS_AT = NOW.plus(Duration.ofDays(30));
    private static final Instant FINISHES_AT = STARTS_AT.plus(Duration.ofHours(3));

    @Mock
    private EventCreator eventCreator;

    private CreateEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new CreateEventUseCase(eventCreator, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void savesNewEventWithGeneratedIdAndCreationInstant() {
        when(eventCreator.save(any(Event.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));
        NewEvent newEvent = new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 5000);

        StepVerifier.create(useCase.run(newEvent))
                .assertNext(event -> {
                    assertThat(event.id()).isNotBlank();
                    assertThat(event.name()).isEqualTo("Concert");
                    assertThat(event.venue()).isEqualTo("Arena");
                    assertThat(event.startsAt()).isEqualTo(STARTS_AT);
                    assertThat(event.finishesAt()).isEqualTo(FINISHES_AT);
                    assertThat(event.capacity()).isEqualTo(5000);
                    assertThat(event.createdAt()).isEqualTo(NOW);
                })
                .verifyComplete();
    }

    @Test
    void emitsSpecificErrorSignalAndDoesNotSaveInvalidEvent() {
        NewEvent newEvent = new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 0);

        StepVerifier.create(useCase.run(newEvent))
                .expectError(InvalidEventCapacityException.class)
                .verify();

        verify(eventCreator, never()).save(any());
    }

    @Test
    void emitsErrorSignalInsteadOfThrowingToTheCaller() {
        NewEvent newEvent = new NewEvent("Concert", "Arena", NOW, FINISHES_AT, 10);

        Mono<Event> result = useCase.run(newEvent); // must not throw at assembly time

        StepVerifier.create(result)
                .expectError(EventStartsInThePastException.class)
                .verify();
    }

    @Test
    void propagatesUnexpectedPersistenceErrors() {
        when(eventCreator.save(any(Event.class))).thenReturn(Mono.error(new IllegalStateException("db down")));
        NewEvent newEvent = new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 10);

        StepVerifier.create(useCase.run(newEvent))
                .expectErrorMessage("db down")
                .verify();
    }
}

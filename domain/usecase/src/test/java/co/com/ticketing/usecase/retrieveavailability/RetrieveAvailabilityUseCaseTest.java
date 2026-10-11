package co.com.ticketing.usecase.retrieveavailability;

import co.com.ticketing.model.event.Availability;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrieveAvailabilityUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Event EVENT = new Event("e-1", "Concert", "Arena", NOW.plusSeconds(3600),
            NOW.plusSeconds(7200), 10, new Inventory(6, 2, 0, 2, 0), NOW);

    @Mock
    private EventRetriever eventRetriever;

    private RetrieveAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetrieveAvailabilityUseCase(eventRetriever, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsTheCountersOfTheEventAtThisInstant() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));

        StepVerifier.create(useCase.run("e-1"))
                .expectNext(new Availability("e-1", 10, 6, 2, 0, 2, 0, NOW))
                .verifyComplete();
    }

    @Test
    void emitsEventNotFoundWhenTheEventDoesNotExist() {
        when(eventRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing"))
                .expectError(EventNotFoundException.class)
                .verify();
    }
}

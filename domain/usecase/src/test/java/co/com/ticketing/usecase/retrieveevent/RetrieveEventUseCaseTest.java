package co.com.ticketing.usecase.retrieveevent;

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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrieveEventUseCaseTest {

    private static final Event EVENT = new Event("e-1", "Concert", "Arena",
            Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000,
            Inventory.initial(5000), Instant.parse("2026-10-09T12:00:00Z"));

    @Mock
    private EventRetriever eventRetriever;

    private RetrieveEventUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RetrieveEventUseCase(eventRetriever);
    }

    @Test
    void returnsTheStoredEvent() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(EVENT));

        StepVerifier.create(useCase.run("e-1"))
                .expectNext(EVENT)
                .verifyComplete();
    }

    @Test
    void emitsEventNotFoundWhenNoEventHasTheId() {
        when(eventRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing"))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventNotFoundException.class)
                        .hasMessage("no event found with id missing"))
                .verify();
    }

    @Test
    void propagatesUnexpectedRetrievalErrors() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.error(new IllegalStateException("db down")));

        StepVerifier.create(useCase.run("e-1"))
                .expectErrorMessage("db down")
                .verify();
    }
}

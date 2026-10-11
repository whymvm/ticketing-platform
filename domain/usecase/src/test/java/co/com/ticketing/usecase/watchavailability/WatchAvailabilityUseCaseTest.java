package co.com.ticketing.usecase.watchavailability;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.event.gateways.InventoryChangeFeed;
import co.com.ticketing.model.exception.EventNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WatchAvailabilityUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock
    private EventRetriever eventRetriever;

    @Mock
    private InventoryChangeFeed inventoryChangeFeed;

    private WatchAvailabilityUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new WatchAvailabilityUseCase(eventRetriever, inventoryChangeFeed, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void startsWithTheCurrentStateAndThenEmitsEachChange() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)));
        when(inventoryChangeFeed.watch("e-1")).thenReturn(Flux.just(withAvailable(8), withAvailable(5)));

        StepVerifier.create(useCase.run("e-1"))
                .assertNext(availability -> assertThat(availability.available()).isEqualTo(10))
                .assertNext(availability -> assertThat(availability.available()).isEqualTo(8))
                .assertNext(availability -> assertThat(availability.available()).isEqualTo(5))
                .verifyComplete();
    }

    @Test
    void doesNotRepeatTheCurrentStateWhenTheSharedFeedSendsItAgain() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)));
        when(inventoryChangeFeed.watch("e-1")).thenReturn(Flux.just(withAvailable(10), withAvailable(9)));

        StepVerifier.create(useCase.run("e-1"))
                .assertNext(availability -> assertThat(availability.available()).isEqualTo(10))
                .assertNext(availability -> assertThat(availability.available()).isEqualTo(9))
                .verifyComplete();
    }

    @Test
    void emitsEventNotFoundWithoutWatchingWhenTheEventDoesNotExist() {
        when(eventRetriever.findById("missing")).thenReturn(Mono.empty());

        StepVerifier.create(useCase.run("missing"))
                .expectError(EventNotFoundException.class)
                .verify();

        verifyNoInteractions(inventoryChangeFeed);
    }

    private static Event withAvailable(int available) {
        return new Event("e-1", "Concert", "Arena", NOW.plusSeconds(3600), NOW.plusSeconds(7200), 10,
                new Inventory(available, 10 - available, 0, 0, 0), NOW);
    }
}

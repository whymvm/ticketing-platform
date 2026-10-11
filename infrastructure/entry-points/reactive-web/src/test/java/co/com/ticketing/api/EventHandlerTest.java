package co.com.ticketing.api;

import co.com.ticketing.model.event.Availability;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.ticketing.usecase.retrieveavailability.RetrieveAvailabilityUseCase;
import co.com.ticketing.usecase.retrieveevent.RetrieveEventUseCase;
import co.com.ticketing.usecase.watchavailability.WatchAvailabilityUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventHandlerTest {

    private static final Instant AS_OF = Instant.parse("2026-10-11T12:00:00Z");
    private static final Availability AVAILABILITY = new Availability("e-1", 10, 8, 2, 0, 0, 0, AS_OF);

    @Mock
    private CreateEventUseCase createEventUseCase;

    @Mock
    private RetrieveEventUseCase retrieveEventUseCase;

    @Mock
    private RetrieveAvailabilityUseCase retrieveAvailabilityUseCase;

    @Mock
    private WatchAvailabilityUseCase watchAvailabilityUseCase;

    @InjectMocks
    private EventHandler handler;

    @Test
    void eachAvailabilityIsANamedEventWithItsInstantAsId() {
        when(watchAvailabilityUseCase.run("e-1")).thenReturn(Flux.just(AVAILABILITY));

        StepVerifier.withVirtualTime(() -> handler.availabilityEvents("e-1"))
                .assertNext(event -> {
                    assertThat(event.data()).isEqualTo(AVAILABILITY);
                    assertThat(event.event()).isEqualTo("availability");
                    assertThat(event.id()).isEqualTo(String.valueOf(AS_OF.toEpochMilli()));
                })
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void sendsAHeartbeatEveryFifteenSecondsToKeepTheConnectionOpen() {
        when(watchAvailabilityUseCase.run("e-1")).thenReturn(Flux.just(AVAILABILITY).concatWith(Flux.never()));

        StepVerifier.withVirtualTime(() -> handler.availabilityEvents("e-1"))
                .assertNext(event -> assertThat(event.data()).isEqualTo(AVAILABILITY))
                .expectNoEvent(Duration.ofSeconds(14))
                .thenAwait(Duration.ofSeconds(1))
                .assertNext(event -> {
                    assertThat(event.comment()).isEqualTo("heartbeat");
                    assertThat(event.data()).isNull();
                })
                .thenAwait(EventHandler.HEARTBEAT_INTERVAL)
                .assertNext(event -> assertThat(event.comment()).isEqualTo("heartbeat"))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }
}

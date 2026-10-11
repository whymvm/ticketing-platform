package co.com.ticketing.usecase.retrieveavailability;

import co.com.ticketing.model.event.Availability;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventNotFoundException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;

@RequiredArgsConstructor
public class RetrieveAvailabilityUseCase {

    private final EventRetriever eventRetriever;
    private final Clock clock;

    /**
     * Returns how many tickets of the event are in each state right now. Emits EventNotFoundException if no
     * event has that id.
     */
    public Mono<Availability> run(String eventId) {
        return eventRetriever.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .map(event -> Availability.of(event, clock.instant()));
    }
}

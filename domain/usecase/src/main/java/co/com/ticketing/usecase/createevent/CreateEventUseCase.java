package co.com.ticketing.usecase.createevent;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.model.event.gateways.EventCreator;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class CreateEventUseCase {

    private final EventCreator eventCreator;
    private final Clock clock;

    /**
     * Creates and saves a new event. Emits an error if the data is invalid.
     */
    public Mono<Event> run(NewEvent newEvent) {
        return Mono.fromCallable(() -> Event.create(UUID.randomUUID().toString(), newEvent, clock.instant()))
                .flatMap(eventCreator::save);
    }
}

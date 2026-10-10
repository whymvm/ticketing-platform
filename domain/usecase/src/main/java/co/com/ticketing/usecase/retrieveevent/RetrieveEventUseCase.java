package co.com.ticketing.usecase.retrieveevent;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventNotFoundException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RetrieveEventUseCase {

    private final EventRetriever eventRetriever;

    /**
     * Retrieves an event by its id. Emits an EventNotFoundException if no event has that id.
     */
    public Mono<Event> run(String id) {
        return eventRetriever.findById(id)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(id)));
    }
}

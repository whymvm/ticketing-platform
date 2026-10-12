package co.com.ticketing.model.event.gateways;

import co.com.ticketing.model.event.Event;
import reactor.core.publisher.Mono;

/**
 * Retrieves stored events.
 */
public interface EventRetriever {

    /**
     * Finds an event by its id.
     *
     * @param id the event id
     * @return the event, or an empty Mono if no event has that id
     */
    Mono<Event> findById(String id);

}

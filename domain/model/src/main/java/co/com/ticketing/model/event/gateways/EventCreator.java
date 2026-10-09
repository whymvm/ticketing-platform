package co.com.ticketing.model.event.gateways;

import co.com.ticketing.model.event.Event;
import reactor.core.publisher.Mono;

/**
 * Persists new events.
 */
public interface EventCreator {

    /**
     * Saves an event already validated by the domain.
     *
     * @param event the event to save
     * @return the saved event
     */
    Mono<Event> save(Event event);

}

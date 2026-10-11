package co.com.ticketing.model.event.gateways;

import co.com.ticketing.model.event.Event;
import reactor.core.publisher.Flux;

/**
 * Notifies the changes in the ticket inventory of an event.
 */
public interface InventoryChangeFeed {

    /**
     * Watches an event and emits it every time its inventory changes. It never completes on its own: it stops
     * when the subscriber cancels.
     *
     * @param eventId the event id
     * @return the event after each change of its inventory
     */
    Flux<Event> watch(String eventId);

}

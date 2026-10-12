package co.com.ticketing.usecase.watchavailability;

import co.com.ticketing.model.event.Availability;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.event.gateways.InventoryChangeFeed;
import co.com.ticketing.model.exception.EventNotFoundException;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;

@RequiredArgsConstructor
public class WatchAvailabilityUseCase {

    private final EventRetriever eventRetriever;
    private final InventoryChangeFeed inventoryChangeFeed;
    private final Clock clock;

    /**
     * Emits the current availability of the event and then a new one every time it changes. Emits
     * EventNotFoundException if no event has that id.
     */
    // The feed is shared by every subscriber of the event, so a subscriber that joins later starts with its own
    // reading of the current state; distinctUntilChanged drops that state if the feed sends it again
    public Flux<Availability> run(String eventId) {
        return eventRetriever.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .flatMapMany(current -> inventoryChangeFeed.watch(eventId).startWith(current))
                .distinctUntilChanged(Event::inventory)
                .map(event -> Availability.of(event, clock.instant()));
    }
}

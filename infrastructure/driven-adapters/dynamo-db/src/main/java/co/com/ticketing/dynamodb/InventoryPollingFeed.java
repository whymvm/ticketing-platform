package co.com.ticketing.dynamodb;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.event.gateways.InventoryChangeFeed;
import lombok.extern.java.Log;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Notifies inventory changes by reading the event once per second. There is a single reading per watched event,
 * shared by all its subscribers: a thousand people watching the same event cost one read per second, not a
 * thousand. In production this would be replaced by DynamoDB Streams without touching the use case.
 */
@Log
@Component
public class InventoryPollingFeed implements InventoryChangeFeed {

    static final Duration POLL_INTERVAL = Duration.ofSeconds(1);

    private final EventRetriever eventRetriever;
    private final Duration pollInterval;
    private final Map<String, Flux<Event>> feeds = new ConcurrentHashMap<>();

    @Autowired
    public InventoryPollingFeed(EventRetriever eventRetriever) {
        this(eventRetriever, POLL_INTERVAL);
    }

    InventoryPollingFeed(EventRetriever eventRetriever, Duration pollInterval) {
        this.eventRetriever = eventRetriever;
        this.pollInterval = pollInterval;
    }

    @Override
    public Flux<Event> watch(String eventId) {
        return feeds.computeIfAbsent(eventId, this::poll);
    }

    // share() reads only while someone is watching: when the last subscriber leaves it cancels the polling, and
    // the next subscriber starts it again. A failed read skips that tick instead of closing every stream
    private Flux<Event> poll(String eventId) {
        return Flux.interval(Duration.ZERO, pollInterval)
                .onBackpressureDrop()
                .concatMap(_ -> eventRetriever.findById(eventId)
                        .doOnError(error -> log.log(Level.WARNING, "Could not read the inventory of " + eventId, error))
                        .onErrorResume(_ -> Mono.empty()))
                .distinctUntilChanged(Event::inventory)
                .share();
    }
}

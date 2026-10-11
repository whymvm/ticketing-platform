package co.com.ticketing.dynamodb;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.event.gateways.EventRetriever;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryPollingFeedTest {

    private static final Duration INTERVAL = Duration.ofSeconds(1);
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Mock
    private EventRetriever eventRetriever;

    private VirtualTimeScheduler scheduler;
    private InventoryPollingFeed feed;

    @BeforeEach
    void setUp() {
        scheduler = VirtualTimeScheduler.getOrSet();
        feed = new InventoryPollingFeed(eventRetriever, INTERVAL);
    }

    @AfterEach
    void tearDown() {
        VirtualTimeScheduler.reset();
    }

    @Test
    void emitsTheEventRightAwayAndThenOnlyWhenItsInventoryChanges() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)), Mono.just(withAvailable(10)),
                Mono.just(withAvailable(7)), Mono.just(withAvailable(7)));

        // Readings at 0, 1, 2 and 3 seconds: 10, 10 (no change), 7, 7 (no change)
        StepVerifier.withVirtualTime(() -> feed.watch("e-1"))
                .assertNext(event -> assertThat(event.inventory().available()).isEqualTo(10))
                .expectNoEvent(INTERVAL)
                .thenAwait(INTERVAL)
                .assertNext(event -> assertThat(event.inventory().available()).isEqualTo(7))
                .expectNoEvent(INTERVAL)
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void subscribersOfTheSameEventShareASingleReadPerInterval() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)));
        List<Event> first = new ArrayList<>();
        List<Event> second = new ArrayList<>();

        Disposable one = feed.watch("e-1").subscribe(first::add);
        Disposable two = feed.watch("e-1").subscribe(second::add);
        scheduler.advanceTimeBy(Duration.ofSeconds(4));

        // 5 ticks (at 0, 1, 2, 3 and 4 seconds) for both subscribers together, not 5 for each one
        verify(eventRetriever, times(5)).findById("e-1");
        assertThat(first).hasSize(1);
        one.dispose();
        two.dispose();
    }

    @Test
    void stopsReadingWhenNobodyIsWatching() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)));

        Disposable subscription = feed.watch("e-1").subscribe();
        scheduler.advanceTimeBy(Duration.ofSeconds(2));
        subscription.dispose();
        scheduler.advanceTimeBy(Duration.ofSeconds(10));

        verify(eventRetriever, times(3)).findById("e-1");
    }

    @Test
    void startsReadingAgainWhenSomeoneWatchesAfterEveryoneLeft() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)));
        List<Event> later = new ArrayList<>();

        feed.watch("e-1").subscribe().dispose();
        Disposable subscription = feed.watch("e-1").subscribe(later::add);
        scheduler.advanceTimeBy(Duration.ZERO);

        assertThat(later).hasSize(1);
        subscription.dispose();
    }

    @Test
    void skipsAFailedReadInsteadOfClosingTheStream() {
        when(eventRetriever.findById("e-1")).thenReturn(Mono.just(withAvailable(10)),
                Mono.error(new IllegalStateException("dynamo down")), Mono.just(withAvailable(6)));

        StepVerifier.withVirtualTime(() -> feed.watch("e-1"))
                .assertNext(event -> assertThat(event.inventory().available()).isEqualTo(10))
                .thenAwait(INTERVAL.multipliedBy(2))
                .assertNext(event -> assertThat(event.inventory().available()).isEqualTo(6))
                .thenCancel()
                .verify(Duration.ofSeconds(5));
    }

    @Test
    void usesOneReadingPerSecondByDefault() {
        assertThat(InventoryPollingFeed.POLL_INTERVAL).hasSeconds(1);
        assertThat(new InventoryPollingFeed(eventRetriever)).isNotNull();
    }

    private static Event withAvailable(int available) {
        return new Event("e-1", "Concert", "Arena", NOW.plusSeconds(3600), NOW.plusSeconds(7200), 10,
                new Inventory(available, 10 - available, 0, 0, 0), NOW);
    }
}

package co.com.ticketing.model.event;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class AvailabilityTest {

    @Test
    void readsEveryCounterOfTheInventory() {
        Instant now = Instant.parse("2026-10-11T12:00:00Z");
        Event event = new Event("e-1", "Concert", "Arena", now.plusSeconds(3600), now.plusSeconds(7200), 10,
                new Inventory(4, 3, 1, 1, 1), now);

        assertThat(Availability.of(event, now)).isEqualTo(new Availability("e-1", 10, 4, 3, 1, 1, 1, now));
    }
}

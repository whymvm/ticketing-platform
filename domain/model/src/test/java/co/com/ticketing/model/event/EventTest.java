package co.com.ticketing.model.event;

import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");
    private static final Instant STARTS_AT = NOW.plus(Duration.ofDays(30));
    private static final Instant FINISHES_AT = STARTS_AT.plus(Duration.ofHours(3));

    @Test
    void createBuildsEventWithEveryTicketAvailable() {
        Event event = Event.create("e-1", new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 5000), NOW);

        assertThat(event).isEqualTo(new Event("e-1", "Concert", "Arena", STARTS_AT, FINISHES_AT, 5000,
                Inventory.initial(5000), NOW));
    }

    @Test
    void createRejectsEventStartingInThePast() {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", NOW, FINISHES_AT, 10)))
                .isInstanceOf(EventStartsInThePastException.class)
                .hasMessage("startsAt must be in the future but was " + NOW);
    }

    @Test
    void storedEventsMayAlreadyHaveStarted() {
        Instant pastStart = NOW.minus(Duration.ofDays(1));

        Event rehydrated = new Event("e-1", "Concert", "Arena", pastStart, pastStart.plusSeconds(60), 10,
                Inventory.initial(10), NOW);

        assertThat(rehydrated.startsAt()).isEqualTo(pastStart);
    }

    @Test
    void salesAreOpenOnlyUntilTheEventStarts() {
        Event event = new Event("e-1", "Concert", "Arena", STARTS_AT, FINISHES_AT, 5000,
                Inventory.initial(5000), NOW);

        assertThat(event.isOpenAt(STARTS_AT.minusSeconds(1))).isTrue();
        assertThat(event.isOpenAt(STARTS_AT)).isFalse();
        assertThat(event.isOpenAt(FINISHES_AT)).isFalse();
    }

    @Test
    void storedEventsAlsoEnforceTheInvariants() {
        Inventory inventory = Inventory.initial(10);

        assertThatThrownBy(() -> new Event("e-1", " ", "Arena", STARTS_AT, FINISHES_AT, 10, inventory, NOW))
                .isInstanceOf(InvalidEventNameException.class);
    }

    @Test
    void rejectsInventoryThatDoesNotAddUpToCapacity() {
        Inventory inventory = new Inventory(8, 1, 0, 0, 0);

        assertThatThrownBy(() -> new Event("e-1", "Concert", "Arena", STARTS_AT, FINISHES_AT, 10, inventory, NOW))
                .isInstanceOf(InconsistentInventoryException.class)
                .hasMessage("inconsistent inventory: counters must add up to 10");
    }

    @Test
    void rejectsMissingInventory() {
        assertThatThrownBy(() -> new Event("e-1", "Concert", "Arena", STARTS_AT, FINISHES_AT, 10, null, NOW))
                .isInstanceOf(InconsistentInventoryException.class);
    }

    @Test
    void createRejectsMissingCapacity() {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, null)))
                .isInstanceOf(InvalidEventCapacityException.class)
                .hasMessage("capacity must be greater than zero but was null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(int capacity) {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, capacity)))
                .isInstanceOf(InvalidEventCapacityException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankName(String name) {
        assertThatThrownBy(creating(new NewEvent(name, "Arena", STARTS_AT, FINISHES_AT, 10)))
                .isInstanceOf(InvalidEventNameException.class);
    }

    @Test
    void rejectsTooLongVenue() {
        String venue = "v".repeat(Event.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(creating(new NewEvent("Concert", venue, STARTS_AT, FINISHES_AT, 10)))
                .isInstanceOf(InvalidEventVenueException.class)
                .hasMessage("venue must not be blank and must have at most 200 characters");
    }

    @Test
    void rejectsFinishNotAfterStart() {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", STARTS_AT, STARTS_AT, 10)))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    void rejectsMissingSchedule() {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", null, FINISHES_AT, 10)))
                .isInstanceOf(InvalidEventScheduleException.class);
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", STARTS_AT, null, 10)))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    void businessExceptionsCarryNoStackTrace() {
        assertThatThrownBy(creating(new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 0)))
                .satisfies(error -> assertThat(error.getStackTrace()).isEmpty());
    }

    // Only Event.create runs inside the lambda, so the assertion cannot pass because of another call
    private static ThrowingCallable creating(NewEvent newEvent) {
        return () -> Event.create("e-1", newEvent, NOW);
    }
}

package co.com.ticketing.model.event;

import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
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
    void createBuildsEventWithCreationInstant() {
        Event event = Event.create("e-1", new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 5000), NOW);

        assertThat(event).isEqualTo(new Event("e-1", "Concert", "Arena", STARTS_AT, FINISHES_AT, 5000, NOW));
    }

    @Test
    void createRejectsEventStartingInThePast() {
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", "Arena", NOW, FINISHES_AT, 10), NOW))
                .isInstanceOf(EventStartsInThePastException.class)
                .hasMessage("startsAt must be in the future but was " + NOW);
    }

    @Test
    void storedEventsMayAlreadyHaveStarted() {
        Instant pastStart = NOW.minus(Duration.ofDays(1));

        Event rehydrated = new Event("e-1", "Concert", "Arena", pastStart, pastStart.plusSeconds(60), 10, NOW);

        assertThat(rehydrated.startsAt()).isEqualTo(pastStart);
    }

    @Test
    void storedEventsAlsoEnforceTheInvariants() {
        assertThatThrownBy(() -> new Event("e-1", " ", "Arena", STARTS_AT, FINISHES_AT, 10, NOW))
                .isInstanceOf(InvalidEventNameException.class);
    }

    @Test
    void createRejectsMissingCapacity() {
        assertThatThrownBy(() -> Event.create("e-1",
                new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, null), NOW))
                .isInstanceOf(InvalidEventCapacityException.class)
                .hasMessage("capacity must be greater than zero but was null");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveCapacity(int capacity) {
        assertThatThrownBy(() -> Event.create("e-1",
                new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, capacity), NOW))
                .isInstanceOf(InvalidEventCapacityException.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsBlankName(String name) {
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent(name, "Arena", STARTS_AT, FINISHES_AT, 10), NOW))
                .isInstanceOf(InvalidEventNameException.class);
    }

    @Test
    void rejectsTooLongVenue() {
        String venue = "v".repeat(Event.MAX_TEXT_LENGTH + 1);

        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", venue, STARTS_AT, FINISHES_AT, 10), NOW))
                .isInstanceOf(InvalidEventVenueException.class)
                .hasMessage("venue must not be blank and must have at most 200 characters");
    }

    @Test
    void rejectsFinishNotAfterStart() {
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", "Arena", STARTS_AT, STARTS_AT, 10), NOW))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    void rejectsMissingSchedule() {
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", "Arena", null, FINISHES_AT, 10), NOW))
                .isInstanceOf(InvalidEventScheduleException.class);
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", "Arena", STARTS_AT, null, 10), NOW))
                .isInstanceOf(InvalidEventScheduleException.class);
    }

    @Test
    void businessExceptionsCarryNoStackTrace() {
        assertThatThrownBy(() -> Event.create("e-1", new NewEvent("Concert", "Arena", STARTS_AT, FINISHES_AT, 0), NOW))
                .satisfies(error -> assertThat(error.getStackTrace()).isEmpty());
    }
}

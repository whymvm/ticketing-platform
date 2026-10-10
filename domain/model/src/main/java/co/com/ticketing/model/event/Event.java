package co.com.ticketing.model.event;


import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

public record Event(String id,
                    String name,
                    String venue,
                    Instant startsAt,
                    Instant finishesAt,
                    Integer capacity,
                    Inventory inventory,
                    Instant createdAt) {

    public static final int MAX_TEXT_LENGTH = 200;


    public Event {
        name = validText(name, () -> new InvalidEventNameException(MAX_TEXT_LENGTH));
        venue = validText(venue, () -> new InvalidEventVenueException(MAX_TEXT_LENGTH));
        finishesAt = validFinish(startsAt, finishesAt);
        capacity = validCapacity(capacity);
        inventory = validInventory(inventory, capacity);
    }

    /**
     * Creates a new event with every ticket available. Rejects events that start in the past.
     */
    public static Event create(String id, NewEvent newEvent, Instant now) {
        Integer capacity = validCapacity(newEvent.capacity());
        Event event = new Event(id, newEvent.name(), newEvent.venue(), newEvent.startsAt(), newEvent.finishesAt(),
                capacity, Inventory.initial(capacity), now);
        return requireFutureStart(event, now);
    }

    private static String validText(String value, Supplier<RuntimeException> error) {
        return Optional.ofNullable(value)
                .filter(text -> !text.isBlank() && text.length() <= MAX_TEXT_LENGTH)
                .orElseThrow(error);
    }

    private static Instant validFinish(Instant startsAt, Instant finishesAt) {
        return Optional.ofNullable(finishesAt)
                .filter(finish -> startsAt != null && finish.isAfter(startsAt))
                .orElseThrow(InvalidEventScheduleException::new);
    }

    private static Integer validCapacity(Integer capacity) {
        return Optional.ofNullable(capacity)
                .filter(value -> value > 0)
                .orElseThrow(() -> new InvalidEventCapacityException(capacity));
    }

    private static Inventory validInventory(Inventory inventory, int capacity) {
        return Optional.ofNullable(inventory)
                .filter(counters -> counters.total() == capacity)
                .orElseThrow(() -> new InconsistentInventoryException("counters must add up to " + capacity));
    }

    private static Event requireFutureStart(Event event, Instant now) {
        return Optional.of(event)
                .filter(candidate -> candidate.startsAt().isAfter(now))
                .orElseThrow(() -> new EventStartsInThePastException(event.startsAt()));
    }
}

package co.com.ticketing.model.event;

import co.com.ticketing.model.exception.InconsistentInventoryException;

import java.util.stream.Stream;

/**
 * Counts the tickets of an event by state.
 */
public record Inventory(Integer available,
                        Integer reserved,
                        Integer pendingConfirmation,
                        Integer sold,
                        Integer complimentary) {

    public Inventory {
        if (hasMissingOrNegativeCounters(available, reserved, pendingConfirmation, sold, complimentary)) {
            throw new InconsistentInventoryException("counters must be present and not negative");
        }
    }

    private static boolean hasMissingOrNegativeCounters(Integer... counters) {
        return Stream.of(counters).anyMatch(count -> count == null || count < 0);
    }

    /**
     * Creates the inventory of a new event, with every ticket available.
     */
    public static Inventory initial(int capacity) {
        return new Inventory(capacity, 0, 0, 0, 0);
    }

    /**
     * Returns the sum of every counter, which must match the event capacity.
     */
    public int total() {
        return available + reserved + pendingConfirmation + sold + complimentary;
    }
}
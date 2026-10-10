package co.com.ticketing.model.event;

import co.com.ticketing.model.exception.InconsistentInventoryException;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Counts the tickets of an event by state.
 */
public record Inventory(Integer available,
                        Integer reserved,
                        Integer pendingConfirmation,
                        Integer sold,
                        Integer complimentary) {

    public Inventory {

        List<Integer> counters = Arrays.asList(available, reserved, pendingConfirmation, sold, complimentary);

        if (hasMissingCounters(counters)) {
            throw new InconsistentInventoryException("counters must be present");
        }

        if (hasNegativeCounters(counters)) {
            throw new InconsistentInventoryException("counters must be not negative");
        }
    }

    private static boolean hasMissingCounters(List<Integer> counters) {
        return counters.stream().anyMatch(Objects::isNull);
    }

    private static boolean hasNegativeCounters(List<Integer> counters) {
        return counters.stream().anyMatch(counter -> counter < 0);
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
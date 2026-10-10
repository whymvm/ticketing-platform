package co.com.ticketing.model.exception;

/**
 * Thrown when the inventory counters are negative or do not add up to the event capacity.
 */
public final class InconsistentInventoryException extends RuntimeException {

    public InconsistentInventoryException(String detail) {
        super("inconsistent inventory: " + detail, null, false, false);
    }
}

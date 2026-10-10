package co.com.ticketing.model.exception;

/**
 * Thrown when an order asks for no tickets or for more than allowed.
 */
public final class InvalidOrderQuantityException extends RuntimeException {

    public InvalidOrderQuantityException(Integer quantity, int max) {
        super("quantity must be between 1 and " + max + " but was " + quantity, null, false, false);
    }
}

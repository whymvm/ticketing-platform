package co.com.ticketing.model.exception;

/**
 * Thrown when the customer that places an order is missing or invalid.
 */
public final class InvalidCustomerException extends RuntimeException {

    public InvalidCustomerException(int maxLength) {
        super("customerId must not be blank and must have at most " + maxLength + " characters", null, false, false);
    }
}

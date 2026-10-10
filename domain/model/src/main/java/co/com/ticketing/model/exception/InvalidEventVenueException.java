package co.com.ticketing.model.exception;

public final class InvalidEventVenueException extends RuntimeException {

    public InvalidEventVenueException(int maxLength) {
        super("venue must not be blank and must have at most " + maxLength + " characters", null, false, false);
    }
}

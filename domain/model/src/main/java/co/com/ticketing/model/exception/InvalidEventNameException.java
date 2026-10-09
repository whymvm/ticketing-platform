package co.com.ticketing.model.exception;

public final class InvalidEventNameException extends RuntimeException {

    public InvalidEventNameException(int maxLength) {
        super("name must not be blank and must have at most " + maxLength + " characters", null, false, false);
    }
}

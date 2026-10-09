package co.com.ticketing.model.exception;

public final class InvalidEventCapacityException extends RuntimeException {

    public InvalidEventCapacityException(Integer capacity) {
        super("capacity must be greater than zero but was " + capacity, null, false, false);
    }
}

package co.com.ticketing.model.exception;

/**
 * Thrown when the start or end date is missing, or the event ends before it starts.
 */
public final class InvalidEventScheduleException extends RuntimeException {

    public InvalidEventScheduleException() {
        super("startsAt and finishesAt are required and finishesAt must be after startsAt", null, false, false);
    }
}

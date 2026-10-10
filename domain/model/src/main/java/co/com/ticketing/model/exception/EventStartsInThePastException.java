package co.com.ticketing.model.exception;

import java.time.Instant;

public final class EventStartsInThePastException extends RuntimeException {

    public EventStartsInThePastException(Instant startsAt) {
        super("startsAt must be in the future but was " + startsAt, null, false, false);
    }
}

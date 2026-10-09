package co.com.ticketing.model.event;

import java.time.Instant;

/**
 * Holds the data needed to create an event.
 */
public record NewEvent(String name,
                       String venue,
                       Instant startsAt,
                       Instant finishesAt,
                       Integer capacity) {
}

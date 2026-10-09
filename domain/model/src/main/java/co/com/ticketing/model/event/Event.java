package co.com.ticketing.model.event;


import java.time.Instant;

public record Event(String id,
                    String name,
                    String venue,
                    Instant startsAt,
                    Instant finishesAt,
                    Instant createdAt) {
}

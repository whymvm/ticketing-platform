package co.com.ticketing.model.event;

import java.time.Instant;

/**
 * How many tickets of an event are in each state at a given instant. Only available tickets can be bought;
 * reserved and pending confirmation tickets are held, and sold and complimentary tickets are final.
 */
public record Availability(String eventId,
                           Integer capacity,
                           Integer available,
                           Integer reserved,
                           Integer pendingConfirmation,
                           Integer sold,
                           Integer complimentary,
                           Instant asOf) {

    /**
     * Reads the availability from the inventory of the event.
     */
    public static Availability of(Event event, Instant asOf) {
        Inventory inventory = event.inventory();
        return new Availability(event.id(), event.capacity(), inventory.available(), inventory.reserved(),
                inventory.pendingConfirmation(), inventory.sold(), inventory.complimentary(), asOf);
    }
}

package co.com.ticketing.model.order;

import java.time.Instant;

/**
 * Free tickets given by the organizer. It is created directly in this state, with no queue and no payment, and it
 * is final: it has no transitions. Its tickets are not sold, so the inventory counts them apart.
 */
public record ComplimentaryOrder(OrderDetails details) implements Order {

    /**
     * Issues complimentary tickets of an event for a recipient. The recipient is the customer of the order, so
     * they can retrieve it with their customer id.
     */
    public static ComplimentaryOrder issue(String id, String eventId, NewComplimentary request, Instant now) {
        return new ComplimentaryOrder(new OrderDetails(id, eventId, request.recipientId(), request.quantity(), now));
    }

    @Override
    public OrderStatus status() {
        return OrderStatus.COMPLIMENTARY;
    }

    @Override
    public Instant updatedAt() {
        return details.createdAt();
    }
}

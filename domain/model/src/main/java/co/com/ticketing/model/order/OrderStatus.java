package co.com.ticketing.model.order;

import co.com.ticketing.model.event.TicketStatus;

import java.util.Optional;

/**
 * Name of each order state. The valid transitions are defined by the state types, not here.
 */
public enum OrderStatus {
    QUEUED,
    RESERVED,
    PENDING_CONFIRMATION,
    SOLD,
    REJECTED,
    EXPIRED,
    COMPLIMENTARY;

    /**
     * State of the tickets of an order in this state (ADR 0001). A queued order has no tickets yet; a rejected or
     * expired order gave its tickets back, so they are available again.
     */
    public Optional<TicketStatus> ticketStatus() {
        return switch (this) {
            case QUEUED -> Optional.empty();
            case RESERVED -> Optional.of(TicketStatus.RESERVED);
            case PENDING_CONFIRMATION -> Optional.of(TicketStatus.PENDING_CONFIRMATION);
            case SOLD -> Optional.of(TicketStatus.SOLD);
            case REJECTED, EXPIRED -> Optional.of(TicketStatus.AVAILABLE);
            case COMPLIMENTARY -> Optional.of(TicketStatus.COMPLIMENTARY);
        };
    }
}

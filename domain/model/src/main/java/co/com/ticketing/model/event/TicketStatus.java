package co.com.ticketing.model.event;

/**
 * State of a ticket, as the technical test defines it. The inventory of an event counts its tickets in each state.
 */
public enum TicketStatus {
    AVAILABLE,
    RESERVED,
    PENDING_CONFIRMATION,
    SOLD,
    COMPLIMENTARY
}

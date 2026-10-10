package co.com.ticketing.sqs.sender;

/**
 * Message that asks to reserve the tickets of an order. It only carries the order id: the consumer reads the
 * current order from the database, so duplicated or out-of-order messages cannot apply stale data.
 */
record ReserveTicketsMessage(String type, String orderId) {

    static final String TYPE = "RESERVE_TICKETS";

    static ReserveTicketsMessage of(String orderId) {
        return new ReserveTicketsMessage(TYPE, orderId);
    }
}

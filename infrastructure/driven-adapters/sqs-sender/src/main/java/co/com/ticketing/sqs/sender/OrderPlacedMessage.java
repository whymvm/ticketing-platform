package co.com.ticketing.sqs.sender;

import co.com.ticketing.model.order.OrderPlaced;

import java.time.Instant;

/**
 * SQS message for the OrderPlaced event. It only carries the order id: the consumer reads the current order
 * from the database, so duplicated or out-of-order messages cannot apply stale data.
 */
record OrderPlacedMessage(String type, String orderId, Instant occurredAt) {

    static final String ORDER_PLACED = "ORDER_PLACED";

    static OrderPlacedMessage from(OrderPlaced event) {
        return new OrderPlacedMessage(ORDER_PLACED, event.orderId(), event.occurredAt());
    }
}

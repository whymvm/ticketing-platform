package co.com.ticketing.sqs.listener;

import java.time.Instant;
import java.util.Optional;

/**
 * SQS message published when an order is placed. It only carries the order id.
 */
record OrderPlacedMessage(String type, String orderId, Instant occurredAt) {

    static final String TYPE = "ORDER_PLACED";

    /**
     * Tells if the message is an ORDER_PLACED event with an order id.
     */
    boolean isValid() {
        return TYPE.equals(type) && Optional.ofNullable(orderId).filter(id -> !id.isBlank()).isPresent();
    }
}

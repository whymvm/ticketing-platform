package co.com.ticketing.sqs.listener;

/**
 * Thrown when a message from the queue is not a valid ORDER_PLACED event.
 */
public final class UnsupportedMessageException extends RuntimeException {

    public UnsupportedMessageException(String messageId) {
        super("message " + messageId + " is not a valid ORDER_PLACED event", null, false, false);
    }
}

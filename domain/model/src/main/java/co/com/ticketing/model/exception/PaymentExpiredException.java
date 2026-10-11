package co.com.ticketing.model.exception;

/**
 * Thrown when a payment is notified after the payment time of its order is over.
 */
public final class PaymentExpiredException extends RuntimeException {

    public PaymentExpiredException(String id) {
        super("the payment time of order " + id + " is over", null, false, false);
    }
}

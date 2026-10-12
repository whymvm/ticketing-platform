package co.com.ticketing.model.exception;

/**
 * Thrown when a payment notification does not say whether the payment was approved or declined.
 */
public final class InvalidPaymentNotificationException extends RuntimeException {

    public InvalidPaymentNotificationException() {
        super("result is required and must be APPROVED or DECLINED", null, false, false);
    }
}

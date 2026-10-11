package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.InvalidPaymentNotificationException;

import java.util.Optional;

/**
 * What the payment provider tells about the payment of an order: approved or declined.
 */
public record PaymentNotification(PaymentResult result) {

    public PaymentNotification {
        result = Optional.ofNullable(result).orElseThrow(InvalidPaymentNotificationException::new);
    }
}

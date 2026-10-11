package co.com.ticketing.model.order;

/**
 * Answer of the payment provider. A declined payment is a business answer, not an error.
 */
public enum PaymentResult {
    APPROVED,
    DECLINED
}

package co.com.ticketing.api;

/**
 * Defines the HTTP headers read by the API.
 */
public final class ApiHeaders {

    // There is no authentication yet: the client declares who it is with this header
    public static final String CUSTOMER_ID = "X-Customer-Id";
    public static final String DEFAULT_CUSTOMER_ID = "local-customer";

    // Makes POST /orders safe to repeat; the response tells when it was answered with an existing order
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";
    public static final String IDEMPOTENT_REPLAYED = "Idempotent-Replayed";

    // Seconds the client should wait before checking a queued order again
    public static final String RETRY_AFTER_SECONDS = "1";

    private ApiHeaders() {
    }
}

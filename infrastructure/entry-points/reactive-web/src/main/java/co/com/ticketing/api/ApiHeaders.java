package co.com.ticketing.api;

/**
 * Defines the HTTP headers read by the API.
 */
public final class ApiHeaders {

    // There is no authentication yet: the client declares who it is with this header
    public static final String CUSTOMER_ID = "X-Customer-Id";
    public static final String DEFAULT_CUSTOMER_ID = "local-customer";

    private ApiHeaders() {
    }
}

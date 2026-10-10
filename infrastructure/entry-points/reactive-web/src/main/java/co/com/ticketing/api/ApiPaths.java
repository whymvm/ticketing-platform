package co.com.ticketing.api;

/**
 * Defines the HTTP routes of the API.
 */
public final class ApiPaths {

    public static final String EVENT_ID = "id";
    public static final String EVENTS = "/api/v1/events";
    public static final String EVENT_BY_ID = EVENTS + "/{" + EVENT_ID + "}";

    public static final String ORDER_ID = "id";
    public static final String ORDERS = "/api/v1/orders";
    public static final String ORDER_BY_ID = ORDERS + "/{" + ORDER_ID + "}";

    private ApiPaths() {
    }
}

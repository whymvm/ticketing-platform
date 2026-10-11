package co.com.ticketing.api;

/**
 * Defines the HTTP routes of the API.
 */
public final class ApiPaths {

    public static final String EVENT_ID = "id";
    public static final String EVENTS = "/api/v1/events";
    public static final String EVENT_BY_ID = EVENTS + "/{" + EVENT_ID + "}";
    public static final String EVENT_AVAILABILITY = EVENT_BY_ID + "/availability";
    public static final String EVENT_AVAILABILITY_STREAM = EVENT_AVAILABILITY + "/stream";

    public static final String ORDER_ID = "id";
    public static final String ORDERS = "/api/v1/orders";
    public static final String ORDER_BY_ID = ORDERS + "/{" + ORDER_ID + "}";
    public static final String ORDER_CONFIRMATION = ORDER_BY_ID + "/confirm";
    public static final String ORDER_HISTORY = ORDER_BY_ID + "/history";
    public static final String ORDER_PAYMENT = ORDER_BY_ID + "/payment";

    private ApiPaths() {
    }
}

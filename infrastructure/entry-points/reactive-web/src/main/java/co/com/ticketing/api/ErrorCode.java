package co.com.ticketing.api;

import org.springframework.http.HttpStatus;

/**
 * Lists the error codes returned by the API, with their HTTP status.
 */
public enum ErrorCode {

    INVALID_EVENT_NAME(HttpStatus.BAD_REQUEST),
    INVALID_EVENT_VENUE(HttpStatus.BAD_REQUEST),
    INVALID_EVENT_SCHEDULE(HttpStatus.BAD_REQUEST),
    EVENT_STARTS_IN_THE_PAST(HttpStatus.BAD_REQUEST),
    INVALID_EVENT_CAPACITY(HttpStatus.BAD_REQUEST),
    EVENT_NOT_FOUND(HttpStatus.NOT_FOUND),
    EVENT_CLOSED(HttpStatus.UNPROCESSABLE_CONTENT),
    INVALID_ORDER_QUANTITY(HttpStatus.BAD_REQUEST),
    INVALID_ORDER_EVENT(HttpStatus.BAD_REQUEST),
    INVALID_CUSTOMER(HttpStatus.BAD_REQUEST),
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND),
    IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST),
    INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT),
    RESERVATION_EXPIRED(HttpStatus.CONFLICT),
    ORDER_NOT_RESERVED(HttpStatus.CONFLICT),
    PAYMENT_EXPIRED(HttpStatus.CONFLICT),
    ORDER_NOT_AWAITING_PAYMENT(HttpStatus.CONFLICT),
    INVALID_PAYMENT_NOTIFICATION(HttpStatus.BAD_REQUEST),
    CONCURRENCY_CONFLICT(HttpStatus.CONFLICT),
    MISSING_REQUEST_BODY(HttpStatus.BAD_REQUEST),
    MALFORMED_REQUEST_BODY(HttpStatus.BAD_REQUEST, "Malformed request body"),
    REQUEST_ERROR(HttpStatus.BAD_REQUEST),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status) {
        this(status, null);
    }

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultDetail() {
        return defaultDetail;
    }
}

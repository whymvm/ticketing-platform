package co.com.ticketing.api;

/**
 * Thrown when the request has no body.
 */
public final class MissingRequestBodyException extends RuntimeException {

    public MissingRequestBodyException() {
        super("Request body is required", null, false, false);
    }
}

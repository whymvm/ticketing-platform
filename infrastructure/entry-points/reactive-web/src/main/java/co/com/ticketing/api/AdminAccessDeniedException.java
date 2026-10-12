package co.com.ticketing.api;

/**
 * Thrown when an administrative request does not carry the right admin key.
 */
public final class AdminAccessDeniedException extends RuntimeException {

    public AdminAccessDeniedException() {
        super("this operation requires a valid " + ApiHeaders.ADMIN_KEY + " header", null, false, false);
    }
}

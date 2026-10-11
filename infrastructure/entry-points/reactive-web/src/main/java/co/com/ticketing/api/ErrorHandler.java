package co.com.ticketing.api;

import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import co.com.ticketing.model.exception.InvalidIdempotencyKeyException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.exception.InvalidPaymentNotificationException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.OrderNotAwaitingPaymentException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.exception.PaymentExpiredException;
import co.com.ticketing.model.exception.ReservationExpiredException;
import lombok.extern.java.Log;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.logging.Level;

/**
 * Maps exceptions to problem detail responses (RFC 9457).
 */
@Log
@Component
public class ErrorHandler {

    private static final String CODE_PROPERTY = "code";
    private static final String DOMAIN_EXCEPTIONS = EventNotFoundException.class.getPackageName();

    public Mono<ServerResponse> handle(Throwable error, ServerRequest request) {
        return switch (error) {
            case InvalidEventNameException e -> problem(ErrorCode.INVALID_EVENT_NAME, e.getMessage(), request);
            case InvalidEventVenueException e -> problem(ErrorCode.INVALID_EVENT_VENUE, e.getMessage(), request);
            case InvalidEventScheduleException e -> problem(ErrorCode.INVALID_EVENT_SCHEDULE, e.getMessage(), request);
            case EventStartsInThePastException e ->
                    problem(ErrorCode.EVENT_STARTS_IN_THE_PAST, e.getMessage(), request);
            case InvalidEventCapacityException e -> problem(ErrorCode.INVALID_EVENT_CAPACITY, e.getMessage(), request);
            case EventNotFoundException e -> problem(ErrorCode.EVENT_NOT_FOUND, e.getMessage(), request);
            case EventClosedException e -> problem(ErrorCode.EVENT_CLOSED, e.getMessage(), request);
            case InvalidOrderQuantityException e -> problem(ErrorCode.INVALID_ORDER_QUANTITY, e.getMessage(), request);
            case InvalidOrderEventException e -> problem(ErrorCode.INVALID_ORDER_EVENT, e.getMessage(), request);
            case InvalidCustomerException e -> problem(ErrorCode.INVALID_CUSTOMER, e.getMessage(), request);
            case OrderNotFoundException e -> problem(ErrorCode.ORDER_NOT_FOUND, e.getMessage(), request);
            case MissingIdempotencyKeyException e ->
                    problem(ErrorCode.IDEMPOTENCY_KEY_REQUIRED, e.getMessage(), request);
            case InvalidIdempotencyKeyException e ->
                    problem(ErrorCode.INVALID_IDEMPOTENCY_KEY, e.getMessage(), request);
            case IdempotencyKeyReusedException e -> problem(ErrorCode.IDEMPOTENCY_KEY_REUSED, e.getMessage(), request);
            case ReservationExpiredException e -> problem(ErrorCode.RESERVATION_EXPIRED, e.getMessage(), request);
            case OrderNotReservedException e -> problem(ErrorCode.ORDER_NOT_RESERVED, e.getMessage(), request);
            case PaymentExpiredException e -> problem(ErrorCode.PAYMENT_EXPIRED, e.getMessage(), request);
            case OrderNotAwaitingPaymentException e ->
                    problem(ErrorCode.ORDER_NOT_AWAITING_PAYMENT, e.getMessage(), request);
            case InvalidPaymentNotificationException e ->
                    problem(ErrorCode.INVALID_PAYMENT_NOTIFICATION, e.getMessage(), request);
            case ConcurrencyConflictException e ->
                    problem(ErrorCode.CONCURRENCY_CONFLICT, e.getMessage(), request);
            case MissingRequestBodyException e -> problem(ErrorCode.MISSING_REQUEST_BODY, e.getMessage(), request);
            // A domain rule broken while reading the body (a record that validates itself) keeps its own code
            case ServerWebInputException e when isDomainError(e.getMostSpecificCause()) ->
                    handle(e.getMostSpecificCause(), request);
            case ServerWebInputException _ -> problem(ErrorCode.MALFORMED_REQUEST_BODY, null, request);
            // Keeps the status code of framework errors
            case ResponseStatusException e ->
                    problem(e.getStatusCode(), ErrorCode.REQUEST_ERROR, e.getReason(), request);
            default -> {
                log.log(Level.SEVERE, "Unexpected error on " + request.path(), error);
                yield problem(ErrorCode.INTERNAL_ERROR, null, request);
            }
        };
    }

    private static boolean isDomainError(Throwable error) {
        return error.getClass().getPackageName().equals(DOMAIN_EXCEPTIONS);
    }

    private Mono<ServerResponse> problem(ErrorCode code, String detail, ServerRequest request) {
        return problem(code.status(), code, detail, request);
    }

    private Mono<ServerResponse> problem(HttpStatusCode status, ErrorCode code, String detail, ServerRequest request) {
        String resolvedDetail = detail != null ? detail : code.defaultDetail();
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, resolvedDetail);
        problem.setInstance(URI.create(request.path()));
        problem.setProperty(CODE_PROPERTY, code.name());
        return ServerResponse.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .bodyValue(problem);
    }
}

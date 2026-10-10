package co.com.ticketing.api;

import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
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

    public Mono<ServerResponse> handle(Throwable error, ServerRequest request) {
        return switch (error) {
            case InvalidEventNameException e -> problem(ErrorCode.INVALID_EVENT_NAME, e.getMessage(), request);
            case InvalidEventVenueException e -> problem(ErrorCode.INVALID_EVENT_VENUE, e.getMessage(), request);
            case InvalidEventScheduleException e -> problem(ErrorCode.INVALID_EVENT_SCHEDULE, e.getMessage(), request);
            case EventStartsInThePastException e ->
                    problem(ErrorCode.EVENT_STARTS_IN_THE_PAST, e.getMessage(), request);
            case InvalidEventCapacityException e -> problem(ErrorCode.INVALID_EVENT_CAPACITY, e.getMessage(), request);
            case EventNotFoundException e -> problem(ErrorCode.EVENT_NOT_FOUND, e.getMessage(), request);
            case InvalidOrderQuantityException e -> problem(ErrorCode.INVALID_ORDER_QUANTITY, e.getMessage(), request);
            case InvalidOrderEventException e -> problem(ErrorCode.INVALID_ORDER_EVENT, e.getMessage(), request);
            case InvalidCustomerException e -> problem(ErrorCode.INVALID_CUSTOMER, e.getMessage(), request);
            case MissingRequestBodyException e -> problem(ErrorCode.MISSING_REQUEST_BODY, e.getMessage(), request);
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

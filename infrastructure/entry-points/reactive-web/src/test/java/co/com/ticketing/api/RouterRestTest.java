package co.com.ticketing.api;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.exception.ReservationExpiredException;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.ticketing.usecase.confirmorder.ConfirmOrderUseCase;
import co.com.ticketing.usecase.placeorder.PlaceOrderUseCase;
import co.com.ticketing.usecase.retrieveorder.RetrieveOrderUseCase;
import co.com.ticketing.usecase.retrieveevent.RetrieveEventUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, OrderHandler.class, ErrorHandler.class})
@WebFluxTest
class RouterRestTest {

    private static final String VALID_BODY = """
            {"name":"Concert","venue":"Arena","startsAt":"2026-12-05T01:00:00Z",
             "finishesAt":"2026-12-05T04:00:00Z","capacity":5000}
            """;

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private CreateEventUseCase createEventUseCase;

    @MockitoBean
    private RetrieveEventUseCase retrieveEventUseCase;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private RetrieveOrderUseCase retrieveOrderUseCase;

    @MockitoBean
    private ConfirmOrderUseCase confirmOrderUseCase;

    @Test
    void createEventReturns201WithLocationAndBody() {
        Event event = new Event("e-1", "Concert", "Arena", Instant.parse("2026-12-05T01:00:00Z"),
                Instant.parse("2026-12-05T04:00:00Z"), 5000, Inventory.initial(5000),
                Instant.parse("2026-10-09T12:00:00Z"));
        when(createEventUseCase.run(any(NewEvent.class))).thenReturn(Mono.just(event));

        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_BODY)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/api/v1/events/e-1")
                .expectBody()
                .jsonPath("$.id").isEqualTo("e-1")
                .jsonPath("$.capacity").isEqualTo(5000)
                .jsonPath("$.inventory.available").isEqualTo(5000)
                .jsonPath("$.inventory.sold").isEqualTo(0)
                .jsonPath("$.startsAt").isEqualTo("2026-12-05T01:00:00Z");

        verify(createEventUseCase).run(new NewEvent("Concert", "Arena",
                Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000));
    }

    static Stream<Arguments> expectedExceptions() {
        return Stream.of(
                Arguments.of(new InvalidEventNameException(200), "INVALID_EVENT_NAME"),
                Arguments.of(new InvalidEventVenueException(200), "INVALID_EVENT_VENUE"),
                Arguments.of(new InvalidEventScheduleException(), "INVALID_EVENT_SCHEDULE"),
                Arguments.of(new EventStartsInThePastException(Instant.EPOCH), "EVENT_STARTS_IN_THE_PAST"),
                Arguments.of(new InvalidEventCapacityException(0), "INVALID_EVENT_CAPACITY"));
    }

    @ParameterizedTest
    @MethodSource("expectedExceptions")
    void createEventMapsEachSpecificExceptionToItsOwnCode(RuntimeException error, String expectedCode) {
        when(createEventUseCase.run(any(NewEvent.class))).thenReturn(Mono.error(error));

        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_BODY)
                .exchange()
                .expectStatus().isBadRequest()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo(expectedCode)
                .jsonPath("$.detail").isEqualTo(error.getMessage())
                .jsonPath("$.instance").isEqualTo("/api/v1/events");
    }

    @Test
    void createEventReturns400WhenBodyIsMalformed() {
        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{not-json")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST_BODY");

        verifyNoInteractions(createEventUseCase);
    }

    @Test
    void createEventReturns400WhenBodyIsMissing() {
        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MISSING_REQUEST_BODY")
                .jsonPath("$.detail").isEqualTo("Request body is required");

        verifyNoInteractions(createEventUseCase);
    }

    @Test
    void createEventKeepsFrameworkStatusForUnsupportedMediaType() {
        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.TEXT_PLAIN)
                .bodyValue("plain text")
                .exchange()
                .expectStatus().isEqualTo(415)
                .expectBody()
                .jsonPath("$.code").isEqualTo("REQUEST_ERROR");
    }

    @Test
    void createEventReturns500WithoutLeakingInternalDetails() {
        when(createEventUseCase.run(any(NewEvent.class)))
                .thenReturn(Mono.error(new IllegalStateException("connection refused to localstack:4566")));

        webTestClient.post()
                .uri("/api/v1/events")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(VALID_BODY)
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail").isEqualTo("Unexpected error");
    }

    @Test
    void retrieveEventReturns200WithTheEventAndItsInventory() {
        Event event = new Event("e-1", "Concert", "Arena", Instant.parse("2026-12-05T01:00:00Z"),
                Instant.parse("2026-12-05T04:00:00Z"), 5000, new Inventory(4000, 600, 200, 150, 50),
                Instant.parse("2026-10-09T12:00:00Z"));
        when(retrieveEventUseCase.run("e-1")).thenReturn(Mono.just(event));

        webTestClient.get()
                .uri("/api/v1/events/e-1")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentType(MediaType.APPLICATION_JSON)
                .expectBody()
                .jsonPath("$.id").isEqualTo("e-1")
                .jsonPath("$.name").isEqualTo("Concert")
                .jsonPath("$.capacity").isEqualTo(5000)
                .jsonPath("$.inventory.available").isEqualTo(4000)
                .jsonPath("$.inventory.sold").isEqualTo(150);
    }

    @Test
    void retrieveEventReturns404WhenTheEventDoesNotExist() {
        when(retrieveEventUseCase.run("missing")).thenReturn(Mono.error(new EventNotFoundException("missing")));

        webTestClient.get()
                .uri("/api/v1/events/missing")
                .exchange()
                .expectStatus().isNotFound()
                .expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_NOT_FOUND")
                .jsonPath("$.detail").isEqualTo("no event found with id missing")
                .jsonPath("$.instance").isEqualTo("/api/v1/events/missing");
    }

    @Test
    void retrieveEventReturns500WithoutLeakingInternalDetails() {
        when(retrieveEventUseCase.run("e-1")).thenReturn(Mono.error(new IllegalStateException("dynamo down")));

        webTestClient.get()
                .uri("/api/v1/events/e-1")
                .exchange()
                .expectStatus().is5xxServerError()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
                .jsonPath("$.detail").isEqualTo("Unexpected error");
    }

    private static final QueuedOrder QUEUED_ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2,
            Instant.parse("2026-10-10T12:00:00Z")));

    @Test
    void placeOrderReturns202WithLocationAndTheQueuedOrder() {
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "c-1")).thenReturn(Mono.just(QUEUED_ORDER));

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("X-Customer-Id", "c-1")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().location("/api/v1/orders/o-1")
                .expectBody()
                .jsonPath("$.id").isEqualTo("o-1")
                .jsonPath("$.status").isEqualTo("QUEUED")
                .jsonPath("$.customerId").isEqualTo("c-1")
                .jsonPath("$.expiresAt").doesNotExist()
                .jsonPath("$.rejectionReason").doesNotExist();
    }

    @Test
    void retrieveOrderReturns200WithTheFieldsOfItsState() {
        when(retrieveOrderUseCase.run("o-1", "c-1"))
                .thenReturn(Mono.just(QUEUED_ORDER.reject(RejectionReason.SOLD_OUT)));

        webTestClient.get()
                .uri("/api/v1/orders/o-1")
                .header("X-Customer-Id", "c-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("o-1")
                .jsonPath("$.status").isEqualTo("REJECTED")
                .jsonPath("$.rejectionReason").isEqualTo("SOLD_OUT")
                .jsonPath("$.expiresAt").doesNotExist();
    }

    @Test
    void retrieveOrderReturns404WhenTheOrderIsNotFound() {
        when(retrieveOrderUseCase.run("o-9", "local-customer"))
                .thenReturn(Mono.error(new OrderNotFoundException("o-9")));

        webTestClient.get()
                .uri("/api/v1/orders/o-9")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ORDER_NOT_FOUND")
                .jsonPath("$.detail").isEqualTo("no order found with id o-9")
                .jsonPath("$.instance").isEqualTo("/api/v1/orders/o-9");
    }

    @Test
    void confirmOrderReturns200WithTheSoldOrder() {
        var sold = QUEUED_ORDER.reserve(Instant.parse("2026-10-10T12:00:00Z"), Duration.ofMinutes(10))
                .confirm(Instant.parse("2026-10-10T12:05:00Z"));
        when(confirmOrderUseCase.run("o-1", "c-1")).thenReturn(Mono.just(sold));

        webTestClient.post()
                .uri("/api/v1/orders/o-1/confirm")
                .header("X-Customer-Id", "c-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.id").isEqualTo("o-1")
                .jsonPath("$.status").isEqualTo("SOLD")
                .jsonPath("$.expiresAt").doesNotExist();
    }

    static Stream<Arguments> confirmationConflicts() {
        return Stream.of(
                Arguments.of(new ReservationExpiredException("o-1"), "RESERVATION_EXPIRED"),
                Arguments.of(new OrderNotReservedException("o-1", OrderStatus.REJECTED), "ORDER_NOT_RESERVED"),
                Arguments.of(new ConcurrencyConflictException("o-1"), "CONCURRENCY_CONFLICT"));
    }

    @ParameterizedTest
    @MethodSource("confirmationConflicts")
    void confirmOrderMapsEachConflictToItsOwnCode(RuntimeException error, String expectedCode) {
        when(confirmOrderUseCase.run("o-1", "local-customer")).thenReturn(Mono.error(error));

        webTestClient.post()
                .uri("/api/v1/orders/o-1/confirm")
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo(expectedCode)
                .jsonPath("$.detail").isEqualTo(error.getMessage());
    }

    @Test
    void placeOrderUsesTheDefaultCustomerWhenTheHeaderIsMissing() {
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "local-customer")).thenReturn(Mono.just(QUEUED_ORDER));

        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isAccepted();

        verify(placeOrderUseCase).run(new NewOrder("e-1", 2), "local-customer");
    }

    @Test
    void placeOrderReturns404WhenTheEventDoesNotExist() {
        when(placeOrderUseCase.run(any(NewOrder.class), any()))
                .thenReturn(Mono.error(new EventNotFoundException("e-9")));

        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-9\",\"quantity\":1}")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_NOT_FOUND");
    }

    static Stream<Arguments> invalidOrders() {
        return Stream.of(
                Arguments.of(new InvalidOrderQuantityException(0, 6), "INVALID_ORDER_QUANTITY"),
                Arguments.of(new InvalidOrderEventException(), "INVALID_ORDER_EVENT"),
                Arguments.of(new InvalidCustomerException(64), "INVALID_CUSTOMER"));
    }

    @ParameterizedTest
    @MethodSource("invalidOrders")
    void placeOrderMapsEachValidationErrorToItsOwnCode(RuntimeException error, String expectedCode) {
        when(placeOrderUseCase.run(any(NewOrder.class), any())).thenReturn(Mono.error(error));

        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":1}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo(expectedCode)
                .jsonPath("$.detail").isEqualTo(error.getMessage());
    }

    @Test
    void placeOrderReturns400WhenTheBodyIsMissing() {
        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MISSING_REQUEST_BODY");

        verifyNoInteractions(placeOrderUseCase);
    }
}

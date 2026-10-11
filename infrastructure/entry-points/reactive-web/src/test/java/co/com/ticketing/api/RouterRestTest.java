package co.com.ticketing.api;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.EventClosedException;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import co.com.ticketing.model.exception.IdempotencyKeyReusedException;
import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidIdempotencyKeyException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import co.com.ticketing.model.exception.InvalidPaymentNotificationException;
import co.com.ticketing.model.exception.OrderNotAwaitingPaymentException;
import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.exception.PaymentExpiredException;
import co.com.ticketing.model.exception.OrderNotReservedException;
import co.com.ticketing.model.exception.ReservationExpiredException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.PaymentNotification;
import co.com.ticketing.model.order.PaymentResult;
import co.com.ticketing.model.order.OrderPlacement;
import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.model.event.Availability;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.ticketing.usecase.retrieveavailability.RetrieveAvailabilityUseCase;
import co.com.ticketing.usecase.watchavailability.WatchAvailabilityUseCase;
import co.com.ticketing.usecase.confirmorder.ConfirmOrderUseCase;
import co.com.ticketing.usecase.issuecomplimentary.IssueComplimentaryUseCase;
import co.com.ticketing.usecase.placeorder.PlaceOrderUseCase;
import co.com.ticketing.usecase.recordpayment.RecordPaymentUseCase;
import co.com.ticketing.usecase.retrieveorder.RetrieveOrderUseCase;
import co.com.ticketing.usecase.retrieveorderhistory.RetrieveOrderHistoryUseCase;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, OrderHandler.class, ErrorHandler.class,
        AdminAccess.class})
@WebFluxTest(properties = "security.admin-api-key=" + RouterRestTest.ADMIN_KEY)
class RouterRestTest {

    static final String ADMIN_KEY = "test-admin-key";

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
    private RetrieveAvailabilityUseCase retrieveAvailabilityUseCase;

    @MockitoBean
    private WatchAvailabilityUseCase watchAvailabilityUseCase;

    @MockitoBean
    private PlaceOrderUseCase placeOrderUseCase;

    @MockitoBean
    private RetrieveOrderUseCase retrieveOrderUseCase;

    @MockitoBean
    private ConfirmOrderUseCase confirmOrderUseCase;

    @MockitoBean
    private RecordPaymentUseCase recordPaymentUseCase;

    @MockitoBean
    private RetrieveOrderHistoryUseCase retrieveOrderHistoryUseCase;

    @MockitoBean
    private IssueComplimentaryUseCase issueComplimentaryUseCase;

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

    private static final Availability AVAILABILITY = new Availability("e-1", 5000, 4998, 2, 0, 0, 0,
            Instant.parse("2026-10-11T12:00:00Z"));

    @Test
    void retrieveAvailabilityReturns200WithEveryCounter() {
        when(retrieveAvailabilityUseCase.run("e-1")).thenReturn(Mono.just(AVAILABILITY));

        webTestClient.get()
                .uri("/api/v1/events/e-1/availability")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.eventId").isEqualTo("e-1")
                .jsonPath("$.capacity").isEqualTo(5000)
                .jsonPath("$.available").isEqualTo(4998)
                .jsonPath("$.reserved").isEqualTo(2)
                .jsonPath("$.pendingConfirmation").isEqualTo(0)
                .jsonPath("$.sold").isEqualTo(0)
                .jsonPath("$.complimentary").isEqualTo(0)
                .jsonPath("$.asOf").isEqualTo("2026-10-11T12:00:00Z");
    }

    @Test
    void retrieveAvailabilityReturns404WhenTheEventDoesNotExist() {
        when(retrieveAvailabilityUseCase.run("missing")).thenReturn(Mono.error(new EventNotFoundException("missing")));

        webTestClient.get()
                .uri("/api/v1/events/missing/availability")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_NOT_FOUND");
    }

    @Test
    void streamAvailabilitySendsServerSentEvents() {
        when(retrieveAvailabilityUseCase.run("e-1")).thenReturn(Mono.just(AVAILABILITY));
        when(watchAvailabilityUseCase.run("e-1")).thenReturn(Flux.just(AVAILABILITY));

        Flux<String> body = webTestClient.get()
                .uri("/api/v1/events/e-1/availability/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM)
                .returnResult(String.class)
                .getResponseBody();

        StepVerifier.create(body.take(1))
                .assertNext(data -> assertThat(data).contains("\"available\":4998").contains("\"eventId\":\"e-1\""))
                .verifyComplete();
    }

    @Test
    void streamAvailabilityReturns404InsteadOfAStreamWhenTheEventDoesNotExist() {
        when(retrieveAvailabilityUseCase.run("missing")).thenReturn(Mono.error(new EventNotFoundException("missing")));

        webTestClient.get()
                .uri("/api/v1/events/missing/availability/stream")
                .accept(MediaType.TEXT_EVENT_STREAM)
                .exchange()
                .expectStatus().isNotFound();

        verifyNoInteractions(watchAvailabilityUseCase);
    }

    private static final QueuedOrder QUEUED_ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2,
            Instant.parse("2026-10-10T12:00:00Z")));

    @Test
    void placeOrderReturns202WithLocationAndTheQueuedOrder() {
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "c-1", "key-0001"))
                .thenReturn(Mono.just(OrderPlacement.created(QUEUED_ORDER)));

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("X-Customer-Id", "c-1")
                .header("Idempotency-Key", "key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().location("/api/v1/orders/o-1")
                .expectHeader().valueEquals("Retry-After", "1")
                .expectHeader().valueEquals("Idempotent-Replayed", "false")
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
                .thenReturn(Mono.just(QUEUED_ORDER.reject(RejectionReason.SOLD_OUT, Instant.parse("2026-10-10T12:05:00Z"))));

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
    void confirmOrderReturns202WithTheOrderWaitingForItsPayment() {
        var pending = QUEUED_ORDER.reserve(Instant.parse("2026-10-10T12:00:00Z"), Duration.ofMinutes(10))
                .confirm(Instant.parse("2026-10-10T12:05:00Z"));
        when(confirmOrderUseCase.run("o-1", "c-1")).thenReturn(Mono.just(pending));

        webTestClient.post()
                .uri("/api/v1/orders/o-1/confirm")
                .header("X-Customer-Id", "c-1")
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().location("/api/v1/orders/o-1")
                .expectBody()
                .jsonPath("$.id").isEqualTo("o-1")
                .jsonPath("$.status").isEqualTo("PENDING_CONFIRMATION")
                .jsonPath("$.ticketStatus").isEqualTo("PENDING_CONFIRMATION")
                .jsonPath("$.expiresAt").isEqualTo("2026-10-10T12:10:00Z");
    }

    @Test
    void recordPaymentReturns200WithTheResultOfThePayment() {
        var sold = QUEUED_ORDER.reserve(Instant.parse("2026-10-10T12:00:00Z"), Duration.ofMinutes(10))
                .confirm(Instant.parse("2026-10-10T12:05:00Z")).markPaid(Instant.parse("2026-10-10T12:06:00Z"));
        when(recordPaymentUseCase.run("o-1", new PaymentNotification(PaymentResult.APPROVED)))
                .thenReturn(Mono.just(sold));

        webTestClient.post()
                .uri("/api/v1/orders/o-1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"result\":\"APPROVED\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("SOLD")
                .jsonPath("$.ticketStatus").isEqualTo("SOLD");
    }

    @Test
    void recordPaymentReturns400WhenTheBodyIsMissing() {
        webTestClient.post()
                .uri("/api/v1/orders/o-1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MISSING_REQUEST_BODY");
    }

    @Test
    void recordPaymentReturnsItsOwnCodeWhenTheNotificationHasNoResult() {
        webTestClient.post()
                .uri("/api/v1/orders/o-1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_PAYMENT_NOTIFICATION")
                .jsonPath("$.detail").isEqualTo("result is required and must be APPROVED or DECLINED");

        verifyNoInteractions(recordPaymentUseCase);
    }

    @Test
    void recordPaymentKeepsTheGenericCodeForAnUnknownResult() {
        webTestClient.post()
                .uri("/api/v1/orders/o-1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"result\":\"MAYBE\"}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MALFORMED_REQUEST_BODY");
    }

    static Stream<Arguments> paymentErrors() {
        return Stream.of(
                Arguments.of(new PaymentExpiredException("o-1"), 409, "PAYMENT_EXPIRED"),
                Arguments.of(new OrderNotAwaitingPaymentException("o-1", OrderStatus.EXPIRED), 409,
                        "ORDER_NOT_AWAITING_PAYMENT"),
                Arguments.of(new InvalidPaymentNotificationException(), 400, "INVALID_PAYMENT_NOTIFICATION"));
    }

    @ParameterizedTest
    @MethodSource("paymentErrors")
    void recordPaymentMapsEachErrorToItsOwnCode(RuntimeException error, int status, String code) {
        when(recordPaymentUseCase.run(any(), any())).thenReturn(Mono.error(error));

        webTestClient.post()
                .uri("/api/v1/orders/o-1/payment")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"result\":\"DECLINED\"}")
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.detail").isEqualTo(error.getMessage());
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
    void retrieveOrderHistoryReturnsTheTransitionsOldestFirst() {
        var reserved = QUEUED_ORDER.reserve(Instant.parse("2026-10-10T12:00:01Z"), Duration.ofMinutes(10));
        when(retrieveOrderHistoryUseCase.run("o-1", "c-1"))
                .thenReturn(Flux.just(OrderHistoryEntry.of(QUEUED_ORDER), OrderHistoryEntry.of(reserved)));

        webTestClient.get()
                .uri("/api/v1/orders/o-1/history")
                .header("X-Customer-Id", "c-1")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.orderId").isEqualTo("o-1")
                .jsonPath("$.transitions.length()").isEqualTo(2)
                .jsonPath("$.transitions[0].toStatus").isEqualTo("QUEUED")
                .jsonPath("$.transitions[0].reason").isEqualTo("ORDER_PLACED")
                .jsonPath("$.transitions[0].fromStatus").doesNotExist()
                .jsonPath("$.transitions[0].occurredAt").isEqualTo("2026-10-10T12:00:00Z")
                .jsonPath("$.transitions[1].fromStatus").isEqualTo("QUEUED")
                .jsonPath("$.transitions[1].toStatus").isEqualTo("RESERVED")
                .jsonPath("$.transitions[1].reason").isEqualTo("TICKETS_RESERVED");
    }

    @Test
    void retrieveOrderHistoryReturns404WhenTheOrderIsNotFound() {
        when(retrieveOrderHistoryUseCase.run("o-9", "local-customer"))
                .thenReturn(Flux.error(new OrderNotFoundException("o-9")));

        webTestClient.get()
                .uri("/api/v1/orders/o-9/history")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ORDER_NOT_FOUND");
    }

    @Test
    void placeOrderMarksAReplayedOrderAndReturnsItsCurrentState() {
        var reserved = QUEUED_ORDER.reserve(Instant.parse("2026-10-10T12:00:01Z"), Duration.ofMinutes(10));
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "c-1", "key-0001"))
                .thenReturn(Mono.just(OrderPlacement.replayed(reserved)));

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("X-Customer-Id", "c-1")
                .header("Idempotency-Key", "key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isAccepted()
                .expectHeader().location("/api/v1/orders/o-1")
                .expectHeader().valueEquals("Idempotent-Replayed", "true")
                .expectBody()
                .jsonPath("$.status").isEqualTo("RESERVED");
    }

    @Test
    void placeOrderUsesTheDefaultCustomerWhenTheHeaderIsMissing() {
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "local-customer", "key-0001"))
                .thenReturn(Mono.just(OrderPlacement.created(QUEUED_ORDER)));

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", "key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isAccepted();

        verify(placeOrderUseCase).run(new NewOrder("e-1", 2), "local-customer", "key-0001");
    }

    @Test
    void placeOrderPassesAMissingKeyToTheUseCase() {
        when(placeOrderUseCase.run(new NewOrder("e-1", 2), "local-customer", null))
                .thenReturn(Mono.error(new MissingIdempotencyKeyException()));

        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":2}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
    }

    static Stream<Arguments> idempotencyErrors() {
        return Stream.of(
                Arguments.of(new InvalidIdempotencyKeyException(8, 64), 400, "INVALID_IDEMPOTENCY_KEY"),
                Arguments.of(new IdempotencyKeyReusedException(), 422, "IDEMPOTENCY_KEY_REUSED"));
    }

    @ParameterizedTest
    @MethodSource("idempotencyErrors")
    void placeOrderMapsEachIdempotencyErrorToItsOwnCode(RuntimeException error, int status, String code) {
        when(placeOrderUseCase.run(any(NewOrder.class), any(), any())).thenReturn(Mono.error(error));

        webTestClient.post()
                .uri("/api/v1/orders")
                .header("Idempotency-Key", "key-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":1}")
                .exchange()
                .expectStatus().isEqualTo(status)
                .expectBody()
                .jsonPath("$.code").isEqualTo(code)
                .jsonPath("$.detail").isEqualTo(error.getMessage());
    }

    @Test
    void placeOrderReturns404WhenTheEventDoesNotExist() {
        when(placeOrderUseCase.run(any(NewOrder.class), any(), any()))
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

    @Test
    void placeOrderReturns422WhenTheEventAlreadyStarted() {
        when(placeOrderUseCase.run(any(NewOrder.class), any(), any()))
                .thenReturn(Mono.error(new EventClosedException("e-1")));

        webTestClient.post()
                .uri("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"eventId\":\"e-1\",\"quantity\":1}")
                .exchange()
                .expectStatus().isEqualTo(422)
                .expectBody()
                .jsonPath("$.code").isEqualTo("EVENT_CLOSED")
                .jsonPath("$.detail").isEqualTo("event e-1 already started, so its sales are closed");
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
        when(placeOrderUseCase.run(any(NewOrder.class), any(), any())).thenReturn(Mono.error(error));

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

    private static final NewComplimentary COMPLIMENTARY_REQUEST = new NewComplimentary("guest-001", 2);
    private static final String COMPLIMENTARY_BODY = "{\"recipientId\":\"guest-001\",\"quantity\":2}";

    @Test
    void issueComplimentaryReturns201WithLocationAndTheComplimentaryOrder() {
        ComplimentaryOrder complimentary = ComplimentaryOrder.issue("o-7", "e-1", COMPLIMENTARY_REQUEST,
                Instant.parse("2026-10-11T12:00:00Z"));
        when(issueComplimentaryUseCase.run("e-1", COMPLIMENTARY_REQUEST, "comp-0001"))
                .thenReturn(Mono.just(OrderPlacement.created(complimentary)));

        webTestClient.post()
                .uri("/api/v1/events/e-1/complimentary")
                .header("X-Admin-Key", ADMIN_KEY)
                .header("Idempotency-Key", "comp-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(COMPLIMENTARY_BODY)
                .exchange()
                .expectStatus().isCreated()
                .expectHeader().location("/api/v1/orders/o-7")
                .expectHeader().valueEquals("Idempotent-Replayed", "false")
                .expectBody()
                .jsonPath("$.status").isEqualTo("COMPLIMENTARY")
                .jsonPath("$.ticketStatus").isEqualTo("COMPLIMENTARY")
                .jsonPath("$.customerId").isEqualTo("guest-001")
                .jsonPath("$.quantity").isEqualTo(2)
                .jsonPath("$.expiresAt").doesNotExist();
    }

    @ParameterizedTest
    @MethodSource("deniedAdminKeys")
    void issueComplimentaryReturns403WithoutAValidAdminKey(String adminKey) {
        webTestClient.post()
                .uri("/api/v1/events/e-1/complimentary")
                .headers(headers -> {
                    if (adminKey != null) {
                        headers.add("X-Admin-Key", adminKey);
                    }
                })
                .header("Idempotency-Key", "comp-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(COMPLIMENTARY_BODY)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ADMIN_ACCESS_DENIED");

        verifyNoInteractions(issueComplimentaryUseCase);
    }

    static Stream<String> deniedAdminKeys() {
        return Stream.of(null, "", "wrong-admin-key", ADMIN_KEY + "x");
    }

    @Test
    void issueComplimentaryReturns409WhenThereAreNotEnoughTickets() {
        when(issueComplimentaryUseCase.run(any(), any(NewComplimentary.class), any()))
                .thenReturn(Mono.error(new TicketsSoldOutException("e-1", 2)));

        webTestClient.post()
                .uri("/api/v1/events/e-1/complimentary")
                .header("X-Admin-Key", ADMIN_KEY)
                .header("Idempotency-Key", "comp-0001")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(COMPLIMENTARY_BODY)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.code").isEqualTo("TICKETS_SOLD_OUT")
                .jsonPath("$.detail").isEqualTo("event e-1 does not have 2 tickets available");
    }

    @Test
    void issueComplimentaryReturns400WhenTheBodyIsMissing() {
        webTestClient.post()
                .uri("/api/v1/events/e-1/complimentary")
                .header("X-Admin-Key", ADMIN_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("MISSING_REQUEST_BODY");

        verifyNoInteractions(issueComplimentaryUseCase);
    }
}

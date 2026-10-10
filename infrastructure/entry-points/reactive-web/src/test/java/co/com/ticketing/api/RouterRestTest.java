package co.com.ticketing.api;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.exception.EventStartsInThePastException;
import co.com.ticketing.model.exception.InvalidEventCapacityException;
import co.com.ticketing.model.exception.InvalidEventNameException;
import co.com.ticketing.model.exception.InvalidEventScheduleException;
import co.com.ticketing.model.exception.InvalidEventVenueException;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
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

import java.time.Instant;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ContextConfiguration(classes = {RouterRest.class, EventHandler.class, ErrorHandler.class})
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
}

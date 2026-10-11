package co.com.ticketing.api;

import co.com.ticketing.model.event.Availability;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
import co.com.ticketing.usecase.retrieveavailability.RetrieveAvailabilityUseCase;
import co.com.ticketing.usecase.retrieveevent.RetrieveEventUseCase;
import co.com.ticketing.usecase.watchavailability.WatchAvailabilityUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;


@Component
@RequiredArgsConstructor
public class EventHandler {

    static final Duration HEARTBEAT_INTERVAL = Duration.ofSeconds(15);
    static final String AVAILABILITY_EVENT = "availability";
    static final String HEARTBEAT_COMMENT = "heartbeat";

    private final CreateEventUseCase createEventUseCase;
    private final RetrieveEventUseCase retrieveEventUseCase;
    private final RetrieveAvailabilityUseCase retrieveAvailabilityUseCase;
    private final WatchAvailabilityUseCase watchAvailabilityUseCase;

    public Mono<ServerResponse> createEvent(ServerRequest request) {
        return request.bodyToMono(NewEvent.class)
                .switchIfEmpty(Mono.error(MissingRequestBodyException::new))
                .flatMap(createEventUseCase::run)
                .flatMap(event -> ServerResponse.created(locationOf(event))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(event));
    }

    public Mono<ServerResponse> retrieveEvent(ServerRequest request) {
        return retrieveEventUseCase.run(request.pathVariable(ApiPaths.EVENT_ID))
                .flatMap(event -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(event));
    }

    /**
     * Returns how many tickets of the event are in each state right now.
     */
    public Mono<ServerResponse> retrieveAvailability(ServerRequest request) {
        return retrieveAvailabilityUseCase.run(request.pathVariable(ApiPaths.EVENT_ID))
                .flatMap(availability -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(availability));
    }

    /**
     * Streams the availability of the event with Server-Sent Events: the current state first, and then a new
     * one every time it changes.
     */
    // Once a stream starts, its 200 status is already sent, so the event is checked first to answer 404 instead
    public Mono<ServerResponse> streamAvailability(ServerRequest request) {
        String eventId = request.pathVariable(ApiPaths.EVENT_ID);
        return retrieveAvailabilityUseCase.run(eventId)
                .flatMap(_ -> ServerResponse.ok()
                        .contentType(MediaType.TEXT_EVENT_STREAM)
                        .body(BodyInserters.fromServerSentEvents(availabilityEvents(eventId))));
    }

    // The heartbeat is an SSE comment: clients ignore it, but it keeps proxies from closing an idle connection
    Flux<ServerSentEvent<Availability>> availabilityEvents(String eventId) {
        Flux<ServerSentEvent<Availability>> changes = watchAvailabilityUseCase.run(eventId)
                .map(availability -> ServerSentEvent.builder(availability)
                        .id(String.valueOf(availability.asOf().toEpochMilli()))
                        .event(AVAILABILITY_EVENT)
                        .build());
        Flux<ServerSentEvent<Availability>> heartbeats = Flux.interval(HEARTBEAT_INTERVAL)
                .map(_ -> ServerSentEvent.<Availability>builder().comment(HEARTBEAT_COMMENT).build());
        return Flux.merge(changes, heartbeats);
    }

    private static URI locationOf(Event event) {
        return UriComponentsBuilder.fromPath(ApiPaths.EVENT_BY_ID).build(event.id());
    }
}

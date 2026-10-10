package co.com.ticketing.api;

import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.NewEvent;
import co.com.ticketing.usecase.createevent.CreateEventUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;


@Component
@RequiredArgsConstructor
public class EventHandler {

    private final CreateEventUseCase createEventUseCase;

    public Mono<ServerResponse> createEvent(ServerRequest request) {
        return request.bodyToMono(NewEvent.class)
                .switchIfEmpty(Mono.error(MissingRequestBodyException::new))
                .flatMap(createEventUseCase::run)
                .flatMap(event -> ServerResponse.created(locationOf(event))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(event));
    }

    private static URI locationOf(Event event) {
        return UriComponentsBuilder.fromPath(ApiPaths.EVENT_BY_ID).build(event.id());
    }
}

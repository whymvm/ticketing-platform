package co.com.ticketing.api;

import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.usecase.placeorder.PlaceOrderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OrderHandler {

    private final PlaceOrderUseCase placeOrderUseCase;

    /**
     * Places an order and answers 202 Accepted: its tickets are reserved later, asynchronously.
     */
    public Mono<ServerResponse> placeOrder(ServerRequest request) {
        return request.bodyToMono(NewOrder.class)
                .switchIfEmpty(Mono.error(MissingRequestBodyException::new))
                .flatMap(newOrder -> placeOrderUseCase.run(newOrder, customerId(request)))
                .flatMap(order -> ServerResponse.accepted()
                        .location(locationOf(order))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(order));
    }

    private static String customerId(ServerRequest request) {
        return Optional.ofNullable(request.headers().firstHeader(ApiHeaders.CUSTOMER_ID))
                .orElse(ApiHeaders.DEFAULT_CUSTOMER_ID);
    }

    private static URI locationOf(Order order) {
        return UriComponentsBuilder.fromPath(ApiPaths.ORDER_BY_ID).build(order.id());
    }
}

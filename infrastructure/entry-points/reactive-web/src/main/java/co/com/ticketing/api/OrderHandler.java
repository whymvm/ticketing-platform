package co.com.ticketing.api;

import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.usecase.confirmorder.ConfirmOrderUseCase;
import co.com.ticketing.usecase.placeorder.PlaceOrderUseCase;
import co.com.ticketing.usecase.retrieveorder.RetrieveOrderUseCase;
import co.com.ticketing.usecase.retrieveorderhistory.RetrieveOrderHistoryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
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
    private final RetrieveOrderUseCase retrieveOrderUseCase;
    private final ConfirmOrderUseCase confirmOrderUseCase;
    private final RetrieveOrderHistoryUseCase retrieveOrderHistoryUseCase;

    /**
     * Places an order and answers 202 Accepted: its tickets are reserved later, asynchronously. A retry with the
     * same Idempotency-Key gets the same order, in its current state, marked with Idempotent-Replayed.
     */
    public Mono<ServerResponse> placeOrder(ServerRequest request) {
        String idempotencyKey = request.headers().firstHeader(ApiHeaders.IDEMPOTENCY_KEY);
        return request.bodyToMono(NewOrder.class)
                .switchIfEmpty(Mono.error(MissingRequestBodyException::new))
                .flatMap(newOrder -> placeOrderUseCase.run(newOrder, customerId(request), idempotencyKey))
                .flatMap(placement -> ServerResponse.accepted()
                        .location(locationOf(placement.order()))
                        .header(HttpHeaders.RETRY_AFTER, ApiHeaders.RETRY_AFTER_SECONDS)
                        .header(ApiHeaders.IDEMPOTENT_REPLAYED, String.valueOf(placement.replayed()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OrderResponse.from(placement.order())));
    }

    /**
     * Returns the current state of an order of the customer.
     */
    public Mono<ServerResponse> retrieveOrder(ServerRequest request) {
        return retrieveOrderUseCase.run(request.pathVariable(ApiPaths.ORDER_ID), customerId(request))
                .flatMap(order -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OrderResponse.from(order)));
    }

    /**
     * Confirms the purchase of a reserved order of the customer and returns the sold order.
     */
    public Mono<ServerResponse> confirmOrder(ServerRequest request) {
        return confirmOrderUseCase.run(request.pathVariable(ApiPaths.ORDER_ID), customerId(request))
                .flatMap(order -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OrderResponse.from(order)));
    }

    /**
     * Returns every transition of an order of the customer, oldest first.
     */
    public Mono<ServerResponse> retrieveOrderHistory(ServerRequest request) {
        String orderId = request.pathVariable(ApiPaths.ORDER_ID);
        return retrieveOrderHistoryUseCase.run(orderId, customerId(request))
                .collectList()
                .flatMap(entries -> ServerResponse.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(OrderHistoryResponse.of(orderId, entries)));
    }

    private static String customerId(ServerRequest request) {
        return Optional.ofNullable(request.headers().firstHeader(ApiHeaders.CUSTOMER_ID))
                .orElse(ApiHeaders.DEFAULT_CUSTOMER_ID);
    }

    private static URI locationOf(Order order) {
        return UriComponentsBuilder.fromPath(ApiPaths.ORDER_BY_ID).build(order.details().id());
    }
}

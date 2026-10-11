package co.com.ticketing.usecase.retrieveorderhistory;

import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.gateways.OrderHistoryRetriever;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RetrieveOrderHistoryUseCase {

    private final OrderRetriever orderRetriever;
    private final OrderHistoryRetriever orderHistoryRetriever;

    /**
     * Returns every transition of an order of the customer, oldest first. Emits OrderNotFoundException if no
     * order has that id or it belongs to another customer, so other customers cannot find out that it exists.
     */
    public Flux<OrderHistoryEntry> run(String orderId, String customerId) {
        return orderRetriever.findById(orderId)
                .filter(order -> order.belongsTo(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(orderId)))
                // Deferred, so the history is not even requested when the order is not found
                .thenMany(Flux.defer(() -> orderHistoryRetriever.findByOrderId(orderId)));
    }
}

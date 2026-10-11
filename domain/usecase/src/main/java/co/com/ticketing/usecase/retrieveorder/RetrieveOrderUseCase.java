package co.com.ticketing.usecase.retrieveorder;

import co.com.ticketing.model.exception.OrderNotFoundException;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
public class RetrieveOrderUseCase {

    private final OrderRetriever orderRetriever;

    /**
     * Retrieves an order of the customer by its id. Emits an OrderNotFoundException if no order has that id
     * or it belongs to another customer, so other customers cannot find out that it exists.
     */
    public Mono<Order> run(String id, String customerId) {
        return orderRetriever.findById(id)
                .filter(order -> order.belongsTo(customerId))
                .switchIfEmpty(Mono.error(() -> new OrderNotFoundException(id)));
    }
}

package co.com.ticketing.usecase.placeorder;

import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventNotFoundException;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.OrderPlaced;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.UUID;

@RequiredArgsConstructor
public class PlaceOrderUseCase {

    private final EventRetriever eventRetriever;
    private final OrderCreator orderCreator;
    private final OrderPublisher orderPublisher;
    private final Clock clock;

    /**
     * Places an order for an existing event, saves it and publishes OrderPlaced so its tickets are reserved later.
     * Emits an error if the data is invalid or the event does not exist.
     */
    public Mono<QueuedOrder> run(NewOrder newOrder, String customerId) {
        return Mono.fromCallable(() -> QueuedOrder.create(UUID.randomUUID().toString(), newOrder, customerId,
                        clock.instant()))
                .flatMap(this::requireExistingEvent)
                .flatMap(orderCreator::save)
                .flatMap(order -> orderPublisher.publish(OrderPlaced.from(order)).thenReturn(order));
    }

    private Mono<QueuedOrder> requireExistingEvent(QueuedOrder order) {
        String eventId = order.details().eventId();
        return eventRetriever.findById(eventId)
                .switchIfEmpty(Mono.error(() -> new EventNotFoundException(eventId)))
                .thenReturn(order);
    }
}

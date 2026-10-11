package co.com.ticketing.api;

import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.TransitionReason;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * History of an order as the API returns it: the order id once, and its transitions oldest first.
 */
public record OrderHistoryResponse(String orderId, List<Transition> transitions) {

    /**
     * One transition. The first one has no previous state, so fromStatus is not returned.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Transition(OrderStatus fromStatus, OrderStatus toStatus, TransitionReason reason,
                             Instant occurredAt) {
    }

    /**
     * Builds the response from the history entries of the order.
     */
    public static OrderHistoryResponse of(String orderId, List<OrderHistoryEntry> entries) {
        return new OrderHistoryResponse(orderId, entries.stream()
                .map(entry -> new Transition(entry.fromStatus(), entry.toStatus(), entry.reason(), entry.occurredAt()))
                .toList());
    }
}

package co.com.ticketing.api;

import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Order as the API returns it: one flat object with a status. In the domain each status is its own type.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrderResponse(String id,
                            String eventId,
                            String customerId,
                            Integer quantity,
                            OrderStatus status,
                            Instant expiresAt,
                            RejectionReason rejectionReason,
                            Instant createdAt) {

    /**
     * Builds the response with the fields of the order state.
     */
    public static OrderResponse from(Order order) {
        return switch (order) {
            case QueuedOrder queued -> of(queued, null, null);
            case ReservedOrder reserved -> of(reserved, reserved.expiresAt(), null);
            case RejectedOrder rejected -> of(rejected, null, rejected.rejectionReason());
            case ExpiredOrder expired -> of(expired, null, null);
            case SoldOrder sold -> of(sold, null, null);
        };
    }

    private static OrderResponse of(Order order, Instant expiresAt, RejectionReason rejectionReason) {
        OrderDetails details = order.details();
        return new OrderResponse(details.id(), details.eventId(), details.customerId(), details.quantity(),
                order.status(), expiresAt, rejectionReason, details.createdAt());
    }
}

package co.com.ticketing.api;

import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderResponseTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final QueuedOrder QUEUED = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW));

    @Test
    void aQueuedOrderHasNoExpirationNorReason() {
        assertThat(OrderResponse.from(QUEUED)).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.QUEUED, null, null, NOW));
    }

    @Test
    void aReservedOrderHasItsExpiration() {
        assertThat(OrderResponse.from(QUEUED.reserve(NOW, Duration.ofMinutes(10)))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.RESERVED,
                        Instant.parse("2026-10-10T12:10:00Z"), null, NOW));
    }

    @Test
    void aRejectedOrderHasItsReason() {
        assertThat(OrderResponse.from(QUEUED.reject(RejectionReason.SOLD_OUT))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.REJECTED, null, RejectionReason.SOLD_OUT,
                        NOW));
    }
}

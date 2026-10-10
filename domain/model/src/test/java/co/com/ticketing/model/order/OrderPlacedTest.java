package co.com.ticketing.model.order;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OrderPlacedTest {

    @Test
    void carriesTheOrderIdAndWhenItWasPlaced() {
        Instant createdAt = Instant.parse("2026-10-10T12:00:00Z");
        Order order = new Order("o-1", "e-1", "c-1", 2, OrderStatus.QUEUED, createdAt);

        assertThat(OrderPlaced.from(order)).isEqualTo(new OrderPlaced("o-1", createdAt));
    }
}

package co.com.ticketing.model.order;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final OrderDetails DETAILS = new OrderDetails("o-1", "e-1", "c-1", 2, NOW);
    private static final QueuedOrder QUEUED = new QueuedOrder(DETAILS);

    @Test
    void createQueuesTheOrder() {
        QueuedOrder order = QueuedOrder.create("o-1", new NewOrder("e-1", 2), "c-1", NOW);

        assertThat(order).isEqualTo(QUEUED);
        assertThat(order.status()).isEqualTo(OrderStatus.QUEUED);
    }

    @Test
    void reserveHoldsTheTicketsUntilTheTimeToLiveEnds() {
        ReservedOrder reserved = QUEUED.reserve(NOW, Duration.ofMinutes(10));

        assertThat(reserved.details()).isEqualTo(DETAILS);
        assertThat(reserved.expiresAt()).isEqualTo(Instant.parse("2026-10-10T12:10:00Z"));
        assertThat(reserved.status()).isEqualTo(OrderStatus.RESERVED);
    }

    @Test
    void rejectKeepsTheReason() {
        RejectedOrder rejected = QUEUED.reject(RejectionReason.SOLD_OUT);

        assertThat(rejected.details()).isEqualTo(DETAILS);
        assertThat(rejected.rejectionReason()).isEqualTo(RejectionReason.SOLD_OUT);
        assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void belongsToTheCustomerWhoPlacedIt() {
        assertThat(QUEUED.belongsTo("c-1")).isTrue();
        assertThat(QUEUED.belongsTo("c-2")).isFalse();
    }

    @Test
    void aReservedOrderAlwaysHasAnExpiration() {
        assertThatThrownBy(() -> new ReservedOrder(DETAILS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expiresAt is required");
    }

    @Test
    void aRejectedOrderAlwaysHasAReason() {
        assertThatThrownBy(() -> new RejectedOrder(DETAILS, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rejectionReason is required");
    }
}

package co.com.ticketing.model.order;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderHistoryEntryTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(3));
    private static final QueuedOrder QUEUED = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW));

    @Test
    void placingAnOrderHasNoPreviousState() {
        assertThat(OrderHistoryEntry.of(QUEUED)).isEqualTo(
                new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED, TransitionReason.ORDER_PLACED, NOW));
    }

    @Test
    void eachStateRecordsWhereItCameFromWhyAndWhen() {
        ReservedOrder reserved = QUEUED.reserve(LATER, Duration.ofMinutes(10));

        assertThat(OrderHistoryEntry.of(reserved)).isEqualTo(new OrderHistoryEntry("o-1", OrderStatus.QUEUED,
                OrderStatus.RESERVED, TransitionReason.TICKETS_RESERVED, LATER));
        assertThat(OrderHistoryEntry.of(reserved.confirm(LATER.plusSeconds(60)))).isEqualTo(new OrderHistoryEntry(
                "o-1", OrderStatus.RESERVED, OrderStatus.SOLD, TransitionReason.ORDER_CONFIRMED, LATER.plusSeconds(60)));
        assertThat(OrderHistoryEntry.of(reserved.expire(LATER.plusSeconds(600)))).isEqualTo(new OrderHistoryEntry(
                "o-1", OrderStatus.RESERVED, OrderStatus.EXPIRED, TransitionReason.RESERVATION_EXPIRED,
                LATER.plusSeconds(600)));
    }

    @Test
    void aRejectionKeepsItsReason() {
        assertThat(OrderHistoryEntry.of(QUEUED.reject(RejectionReason.SOLD_OUT, LATER)).reason())
                .isEqualTo(TransitionReason.SOLD_OUT);
        assertThat(OrderHistoryEntry.of(QUEUED.reject(RejectionReason.EVENT_CLOSED, LATER)).reason())
                .isEqualTo(TransitionReason.EVENT_CLOSED);
        assertThat(OrderHistoryEntry.of(QUEUED.reject(RejectionReason.SOLD_OUT, LATER)).fromStatus())
                .isEqualTo(OrderStatus.QUEUED);
    }

    @Test
    void anEntryAlwaysHasItsNewStateReasonAndInstant() {
        assertThatThrownBy(() -> new OrderHistoryEntry("o-1", null, null, TransitionReason.ORDER_PLACED, NOW))
                .hasMessage("toStatus is required");
        assertThatThrownBy(() -> new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED, null, NOW))
                .hasMessage("reason is required");
        assertThatThrownBy(() -> new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED, TransitionReason.ORDER_PLACED,
                null)).hasMessage("occurredAt is required");
    }
}

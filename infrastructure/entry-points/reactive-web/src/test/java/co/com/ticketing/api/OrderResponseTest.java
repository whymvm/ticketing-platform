package co.com.ticketing.api;

import co.com.ticketing.model.event.TicketStatus;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.NewComplimentary;
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
    private static final Instant LATER = NOW.plus(Duration.ofMinutes(5));
    private static final QueuedOrder QUEUED = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW));

    @Test
    void aQueuedOrderHasNoTicketsYetNorExpirationNorReason() {
        assertThat(OrderResponse.from(QUEUED)).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.QUEUED, null, null, null, NOW, NOW));
    }

    @Test
    void aReservedOrderHasItsExpirationAndReservedTickets() {
        assertThat(OrderResponse.from(QUEUED.reserve(LATER, Duration.ofMinutes(10)))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.RESERVED, TicketStatus.RESERVED,
                        LATER.plus(Duration.ofMinutes(10)), null, NOW, LATER));
    }

    @Test
    void anOrderWaitingForItsPaymentHasThePaymentDeadline() {
        assertThat(OrderResponse.from(QUEUED.reserve(NOW, Duration.ofMinutes(10)).confirm(LATER))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.PENDING_CONFIRMATION,
                        TicketStatus.PENDING_CONFIRMATION, LATER.plus(Duration.ofMinutes(5)), null, NOW, LATER));
    }

    @Test
    void aSoldOrderHasSoldTickets() {
        Instant paidAt = LATER.plusSeconds(30);
        assertThat(OrderResponse.from(QUEUED.reserve(NOW, Duration.ofMinutes(10)).confirm(LATER).markPaid(paidAt)))
                .isEqualTo(new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.SOLD, TicketStatus.SOLD, null, null,
                        NOW, paidAt));
    }

    @Test
    void anExpiredOrderGaveItsTicketsBack() {
        assertThat(OrderResponse.from(QUEUED.reserve(NOW, Duration.ofMinutes(10)).expire(LATER))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.EXPIRED, TicketStatus.AVAILABLE, null, null,
                        NOW, LATER));
    }

    @Test
    void aRejectedOrderHasItsReasonAndGaveItsTicketsBack() {
        assertThat(OrderResponse.from(QUEUED.reject(RejectionReason.SOLD_OUT, LATER))).isEqualTo(
                new OrderResponse("o-1", "e-1", "c-1", 2, OrderStatus.REJECTED, TicketStatus.AVAILABLE, null,
                        RejectionReason.SOLD_OUT, NOW, LATER));
    }

    @Test
    void aComplimentaryOrderHasComplimentaryTicketsAndNothingToExpire() {
        assertThat(OrderResponse.from(ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("guest-001", 2), NOW)))
                .isEqualTo(new OrderResponse("o-1", "e-1", "guest-001", 2, OrderStatus.COMPLIMENTARY,
                        TicketStatus.COMPLIMENTARY, null, null, NOW, NOW));
    }
}

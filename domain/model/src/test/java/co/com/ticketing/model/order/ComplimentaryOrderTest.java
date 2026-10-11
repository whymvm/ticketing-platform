package co.com.ticketing.model.order;

import co.com.ticketing.model.event.TicketStatus;
import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComplimentaryOrderTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");

    @Test
    void issuesTheTicketsForTheRecipient() {
        ComplimentaryOrder order = ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("guest-001", 4), NOW);

        assertThat(order.details()).isEqualTo(new OrderDetails("o-1", "e-1", "guest-001", 4, NOW));
        assertThat(order.status()).isEqualTo(OrderStatus.COMPLIMENTARY);
        assertThat(order.updatedAt()).isEqualTo(NOW);
        assertThat(order.belongsTo("guest-001")).isTrue();
    }

    @Test
    void itsTicketsAreComplimentaryAndItsHistoryStartsWhenItIsIssued() {
        ComplimentaryOrder order = ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("guest-001", 2), NOW);

        assertThat(OrderStatus.COMPLIMENTARY.ticketStatus()).contains(TicketStatus.COMPLIMENTARY);
        assertThat(OrderHistoryEntry.of(order)).isEqualTo(new OrderHistoryEntry("o-1", null,
                OrderStatus.COMPLIMENTARY, TransitionReason.COMPLIMENTARY_ISSUED, NOW));
    }

    @Test
    void followsTheSameRulesAsAnOrder() {
        assertThatThrownBy(() -> ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary("guest-001", 0), NOW))
                .isInstanceOf(InvalidOrderQuantityException.class);
        assertThatThrownBy(() -> ComplimentaryOrder.issue("o-1", "e-1", new NewComplimentary(" ", 1), NOW))
                .isInstanceOf(InvalidCustomerException.class);
    }
}

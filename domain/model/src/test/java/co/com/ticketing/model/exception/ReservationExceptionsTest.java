package co.com.ticketing.model.exception;

import co.com.ticketing.model.order.OrderStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReservationExceptionsTest {

    @Test
    void ticketsSoldOutNamesTheEventAndTheQuantity() {
        TicketsSoldOutException error = new TicketsSoldOutException("e-1", 2);

        assertThat(error).hasMessage("event e-1 does not have 2 tickets available");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void orderAlreadyProcessedNamesTheOrder() {
        OrderAlreadyProcessedException error = new OrderAlreadyProcessedException("o-1");

        assertThat(error).hasMessage("order o-1 was already processed");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void reservationExpiredNamesTheOrder() {
        ReservationExpiredException error = new ReservationExpiredException("o-1");

        assertThat(error).hasMessage("the reservation of order o-1 has expired");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void orderNotReservedNamesTheOrderAndItsStatus() {
        OrderNotReservedException error = new OrderNotReservedException("o-1", OrderStatus.REJECTED);

        assertThat(error).hasMessage("order o-1 cannot be confirmed because it is REJECTED");
        assertThat(error.getStackTrace()).isEmpty();
    }

    @Test
    void concurrencyConflictNamesTheOrder() {
        ConcurrencyConflictException error = new ConcurrencyConflictException("o-1");

        assertThat(error).hasMessage("order o-1 conflicted with a concurrent update");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

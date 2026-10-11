package co.com.ticketing.model.order;

import co.com.ticketing.model.event.TicketStatus;
import co.com.ticketing.model.exception.InvalidPaymentNotificationException;
import co.com.ticketing.model.exception.OrderNotAwaitingPaymentException;
import co.com.ticketing.model.exception.PaymentExpiredException;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTest {

    @Test
    void aNotificationSaysWhetherThePaymentWasApprovedOrDeclined() {
        assertThat(new PaymentNotification(PaymentResult.APPROVED).result()).isEqualTo(PaymentResult.APPROVED);
        assertThatThrownBy(() -> new PaymentNotification(null))
                .isInstanceOf(InvalidPaymentNotificationException.class)
                .hasMessage("result is required and must be APPROVED or DECLINED");
    }

    @Test
    void eachOrderStateTellsTheStateOfItsTickets() {
        assertThat(OrderStatus.QUEUED.ticketStatus()).isEmpty();
        assertThat(OrderStatus.RESERVED.ticketStatus()).contains(TicketStatus.RESERVED);
        assertThat(OrderStatus.PENDING_CONFIRMATION.ticketStatus()).contains(TicketStatus.PENDING_CONFIRMATION);
        assertThat(OrderStatus.SOLD.ticketStatus()).contains(TicketStatus.SOLD);
        assertThat(OrderStatus.REJECTED.ticketStatus()).isEqualTo(Optional.of(TicketStatus.AVAILABLE));
        assertThat(OrderStatus.EXPIRED.ticketStatus()).isEqualTo(Optional.of(TicketStatus.AVAILABLE));
    }

    @Test
    void paymentExceptionsNameTheOrderWithoutStackTrace() {
        assertThat(new PaymentExpiredException("o-1")).hasMessage("the payment time of order o-1 is over");
        assertThat(new OrderNotAwaitingPaymentException("o-1", OrderStatus.EXPIRED))
                .hasMessage("order o-1 is not waiting for its payment because it is EXPIRED");
        assertThat(new PaymentExpiredException("o-1").getStackTrace()).isEmpty();
        assertThat(new OrderNotAwaitingPaymentException("o-1", OrderStatus.SOLD).getStackTrace()).isEmpty();
        assertThat(new InvalidPaymentNotificationException().getStackTrace()).isEmpty();
    }
}

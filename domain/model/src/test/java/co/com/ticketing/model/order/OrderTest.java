package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.PaymentExpiredException;
import co.com.ticketing.model.exception.ReservationExpiredException;
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
        RejectedOrder rejected = QUEUED.reject(RejectionReason.SOLD_OUT, NOW);

        assertThat(rejected.details()).isEqualTo(DETAILS);
        assertThat(rejected.rejectionReason()).isEqualTo(RejectionReason.SOLD_OUT);
        assertThat(rejected.status()).isEqualTo(OrderStatus.REJECTED);
    }

    @Test
    void aReservationExpiresWhenItsTimeIsOver() {
        ReservedOrder reserved = QUEUED.reserve(NOW, Duration.ofMinutes(10));

        assertThat(reserved.isExpiredAt(NOW.plus(Duration.ofMinutes(9)))).isFalse();
        assertThat(reserved.isExpiredAt(NOW.plus(Duration.ofMinutes(10)))).isTrue();
        assertThat(reserved.isExpiredAt(NOW.plus(Duration.ofMinutes(11)))).isTrue();
    }

    @Test
    void expireKeepsTheDetails() {
        ExpiredOrder expired = QUEUED.reserve(NOW, Duration.ofMinutes(10)).expire(NOW);

        assertThat(expired.details()).isEqualTo(DETAILS);
        assertThat(expired.status()).isEqualTo(OrderStatus.EXPIRED);
        assertThat(expired.expirationReason()).isEqualTo(ExpirationReason.RESERVATION_TIMEOUT);
    }

    @Test
    void confirmPutsAValidReservationToWaitForItsPaymentForFiveMinutes() {
        Instant confirmedAt = NOW.plus(Duration.ofMinutes(9));

        PendingConfirmationOrder pending = QUEUED.reserve(NOW, Duration.ofMinutes(10)).confirm(confirmedAt);

        assertThat(pending.details()).isEqualTo(DETAILS);
        assertThat(pending.status()).isEqualTo(OrderStatus.PENDING_CONFIRMATION);
        assertThat(pending.expiresAt()).isEqualTo(confirmedAt.plus(Duration.ofMinutes(5)));
        assertThat(pending.updatedAt()).isEqualTo(confirmedAt);
    }

    @Test
    void anApprovedPaymentSellsTheTickets() {
        SoldOrder sold = pendingSince(NOW).markPaid(NOW.plus(Duration.ofMinutes(4)));

        assertThat(sold.status()).isEqualTo(OrderStatus.SOLD);
        assertThat(sold.updatedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(4)));
    }

    @Test
    void aDeclinedPaymentRejectsTheOrder() {
        RejectedOrder rejected = pendingSince(NOW).declinePayment(NOW.plus(Duration.ofMinutes(1)));

        assertThat(rejected.rejectionReason()).isEqualTo(RejectionReason.PAYMENT_DECLINED);
    }

    @Test
    void aPaymentNotifiedAfterItsTimeIsRefusedEvenBeforeTheTicketsAreReleased() {
        PendingConfirmationOrder pending = pendingSince(NOW);
        Instant late = NOW.plus(PendingConfirmationOrder.PAYMENT_TIME);

        assertThatThrownBy(() -> pending.markPaid(late))
                .isInstanceOf(PaymentExpiredException.class)
                .hasMessage("the payment time of order o-1 is over");
        assertThatThrownBy(() -> pending.declinePayment(late)).isInstanceOf(PaymentExpiredException.class);
        assertThat(pending.isExpiredAt(late)).isTrue();
        assertThat(pending.isExpiredAt(late.minusSeconds(1))).isFalse();
    }

    @Test
    void aPaymentThatNeverArrivesExpiresTheOrder() {
        ExpiredOrder expired = pendingSince(NOW).expire(NOW.plus(Duration.ofMinutes(5)));

        assertThat(expired.expirationReason()).isEqualTo(ExpirationReason.PAYMENT_TIMEOUT);
    }

    @Test
    void confirmRejectsAnExpiredReservationEvenBeforeItsTicketsAreReleased() {
        ReservedOrder reserved = QUEUED.reserve(NOW, Duration.ofMinutes(10));

        Instant afterTheReservation = NOW.plus(Duration.ofMinutes(10));

        assertThatThrownBy(() -> reserved.confirm(afterTheReservation))
                .isInstanceOf(ReservationExpiredException.class)
                .hasMessage("the reservation of order o-1 has expired");
    }

    @Test
    void belongsToTheCustomerWhoPlacedIt() {
        assertThat(QUEUED.belongsTo("c-1")).isTrue();
        assertThat(QUEUED.belongsTo("c-2")).isFalse();
    }

    @Test
    void aReservedOrderAlwaysHasAnExpiration() {
        assertThatThrownBy(() -> new ReservedOrder(DETAILS, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("expiresAt is required");
    }

    @Test
    void everyStateAfterQueuedKnowsWhenItStarted() {
        assertThatThrownBy(() -> new ReservedOrder(DETAILS, NOW, null)).hasMessage("updatedAt is required");
        assertThatThrownBy(() -> new RejectedOrder(DETAILS, RejectionReason.SOLD_OUT, null))
                .hasMessage("updatedAt is required");
        assertThatThrownBy(() -> new ExpiredOrder(DETAILS, ExpirationReason.RESERVATION_TIMEOUT, null))
                .hasMessage("updatedAt is required");
        assertThatThrownBy(() -> new ExpiredOrder(DETAILS, null, NOW)).hasMessage("expirationReason is required");
        assertThatThrownBy(() -> new PendingConfirmationOrder(DETAILS, NOW, null)).hasMessage("updatedAt is required");
        assertThatThrownBy(() -> new PendingConfirmationOrder(DETAILS, null, NOW)).hasMessage("expiresAt is required");
        assertThatThrownBy(() -> new SoldOrder(DETAILS, null)).hasMessage("updatedAt is required");
    }

    @Test
    void aQueuedOrderWasUpdatedWhenItWasCreated() {
        assertThat(QUEUED.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void eachTransitionRecordsWhenItHappened() {
        Instant later = NOW.plus(Duration.ofMinutes(3));

        assertThat(QUEUED.reserve(later, Duration.ofMinutes(10)).updatedAt()).isEqualTo(later);
        assertThat(QUEUED.reject(RejectionReason.SOLD_OUT, later).updatedAt()).isEqualTo(later);
        assertThat(QUEUED.reserve(NOW, Duration.ofMinutes(10)).confirm(later).updatedAt()).isEqualTo(later);
        assertThat(QUEUED.reserve(NOW, Duration.ofMinutes(10)).expire(later).updatedAt()).isEqualTo(later);
    }

    @Test
    void aRejectedOrderAlwaysHasAReason() {
        assertThatThrownBy(() -> new RejectedOrder(DETAILS, null, NOW))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("rejectionReason is required");
    }

    private static PendingConfirmationOrder pendingSince(Instant confirmedAt) {
        return QUEUED.reserve(confirmedAt, Duration.ofMinutes(10)).confirm(confirmedAt);
    }
}

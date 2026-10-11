package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.InvalidIdempotencyKeyException;
import co.com.ticketing.model.exception.MissingIdempotencyKeyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyTest {

    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final IdempotencyKey KEY = new IdempotencyKey("c-1", "key-0001");

    @Test
    void theKeyIsScopedToItsCustomer() {
        assertThat(KEY.scoped()).isEqualTo("c-1#key-0001");
        assertThat(new IdempotencyKey("c-2", "key-0001").scoped()).isNotEqualTo(KEY.scoped());
    }

    @ParameterizedTest
    @ValueSource(strings = {"12345678", "3f6d1d7e-0b0a-4c1e-9a5e-2d9d7c1b8f00", "a_b-C_d-"})
    void acceptsKeysWithTheExpectedFormat(String value) {
        assertThat(new IdempotencyKey("c-1", value).value()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void requiresAKey(String value) {
        assertThatThrownBy(() -> new IdempotencyKey("c-1", value))
                .isInstanceOf(MissingIdempotencyKeyException.class)
                .hasMessage("the Idempotency-Key header is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"short", "has spaces!", "tilde~key"})
    void rejectsKeysWithAnInvalidFormat(String value) {
        assertThatThrownBy(() -> new IdempotencyKey("c-1", value))
                .isInstanceOf(InvalidIdempotencyKeyException.class)
                .hasMessage("the Idempotency-Key must have between 8 and 64 letters, digits, hyphens or underscores");
    }

    @Test
    void rejectsKeysLongerThanSixtyFourCharacters() {
        assertThatThrownBy(() -> new IdempotencyKey("c-1", "x".repeat(IdempotencyKey.MAX_LENGTH + 1)))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(new IdempotencyKey("c-1", "x".repeat(IdempotencyKey.MAX_LENGTH)).value()).hasSize(64);
    }

    @Test
    void theRecordPointsToTheOrderAndLastsTwentyFourHours() {
        QueuedOrder order = QueuedOrder.create("o-1", new NewOrder("e-1", 2), "c-1", NOW);

        IdempotencyRecord record = IdempotencyRecord.of(KEY, order);

        assertThat(record.key()).isEqualTo("c-1#key-0001");
        assertThat(record.orderId()).isEqualTo("o-1");
        assertThat(record.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(record.isExpiredAt(NOW.plus(Duration.ofHours(23)))).isFalse();
        assertThat(record.isExpiredAt(NOW.plus(Duration.ofHours(24)))).isTrue();
        assertThat(record.requestFingerprint()).hasSize(64).matches("[0-9a-f]+");
    }

    @Test
    void theSameDataGivesTheSameFingerprintEvenForAnotherOrderId() {
        IdempotencyRecord first = IdempotencyRecord.of(KEY, QueuedOrder.create("o-1", new NewOrder("e-1", 2), "c-1", NOW));
        IdempotencyRecord retry = IdempotencyRecord.of(KEY,
                QueuedOrder.create("o-2", new NewOrder("e-1", 2), "c-1", NOW.plusSeconds(5)));
        IdempotencyRecord otherQuantity = IdempotencyRecord.of(KEY,
                QueuedOrder.create("o-3", new NewOrder("e-1", 3), "c-1", NOW));
        IdempotencyRecord otherEvent = IdempotencyRecord.of(KEY,
                QueuedOrder.create("o-4", new NewOrder("e-2", 2), "c-1", NOW));

        assertThat(first.sameRequestAs(retry)).isTrue();
        assertThat(first.sameRequestAs(otherQuantity)).isFalse();
        assertThat(first.sameRequestAs(otherEvent)).isFalse();
    }

    @Test
    void aPurchaseAndAComplimentaryOrderWithTheSameDataAreDifferentRequests() {
        IdempotencyKey organizerKey = new IdempotencyKey("organizer", "key-0001");
        IdempotencyRecord purchase = IdempotencyRecord.of(organizerKey,
                QueuedOrder.create("o-1", new NewOrder("e-1", 2), "organizer", NOW));
        IdempotencyRecord complimentary = IdempotencyRecord.of(organizerKey,
                ComplimentaryOrder.issue("o-2", "e-1", new NewComplimentary("organizer", 2), NOW));

        assertThat(purchase.key()).isEqualTo(complimentary.key());
        assertThat(purchase.sameRequestAs(complimentary)).isFalse();
    }

    @Test
    void aPlacementTellsIfItWasReplayed() {
        QueuedOrder order = QueuedOrder.create("o-1", new NewOrder("e-1", 2), "c-1", NOW);

        assertThat(OrderPlacement.created(order).replayed()).isFalse();
        assertThat(OrderPlacement.replayed(order).replayed()).isTrue();
    }
}

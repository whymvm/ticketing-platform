package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.TransitionReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class OrderHistoryEntityTest {

    @Test
    void keepsEveryFieldOfTheEntry() {
        OrderHistoryEntry entry = new OrderHistoryEntry("o-1", OrderStatus.QUEUED, OrderStatus.RESERVED,
                TransitionReason.TICKETS_RESERVED, Instant.parse("2026-10-11T12:00:00.5Z"));

        OrderHistoryEntity entity = OrderHistoryEntity.from(entry);

        assertThat(entity.getSk()).isEqualTo("2026-10-11T12:00:00.500000000Z#RESERVED");
        assertThat(entity.toDomain()).isEqualTo(entry);
    }

    @Test
    void theFirstEntryHasNoPreviousState() {
        OrderHistoryEntry entry = new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED,
                TransitionReason.ORDER_PLACED, Instant.parse("2026-10-11T12:00:00Z"));

        OrderHistoryEntity entity = OrderHistoryEntity.from(entry);

        assertThat(entity.getFromStatus()).isNull();
        assertThat(entity.toDomain()).isEqualTo(entry);
    }

    // Plain ISO-8601 text drops trailing zeros ("12:00:00Z" sorts after "12:00:00.5Z"); the fixed width keeps the
    // text order equal to the time order
    @Test
    void sortKeysSortInTimeOrderEvenWithinTheSameSecond() {
        String atTheSecond = sortKeyAt("2026-10-11T12:00:00Z");
        String halfASecondLater = sortKeyAt("2026-10-11T12:00:00.5Z");
        String aSecondLater = sortKeyAt("2026-10-11T12:00:01Z");

        assertThat(Stream.of(aSecondLater, halfASecondLater, atTheSecond).sorted())
                .containsExactly(atTheSecond, halfASecondLater, aSecondLater);
    }

    private static String sortKeyAt(String instant) {
        return OrderHistoryEntity.from(new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED,
                TransitionReason.ORDER_PLACED, Instant.parse(instant))).getSk();
    }
}

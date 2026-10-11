package co.com.ticketing.dynamodb;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExpiringOrdersIndexTest {

    @Test
    void listsEveryShard() {
        assertThat(ExpiringOrdersIndex.shards()).containsExactly("0", "1", "2", "3");
    }

    @Test
    void alwaysPutsAnOrderInTheSameValidShard() {
        IntStream.range(0, 100).mapToObj(i -> "order-" + i).forEach(id -> {
            assertThat(ExpiringOrdersIndex.shards()).contains(ExpiringOrdersIndex.shardOf(id));
            assertThat(ExpiringOrdersIndex.shardOf(id)).isEqualTo(ExpiringOrdersIndex.shardOf(id));
        });
    }
}

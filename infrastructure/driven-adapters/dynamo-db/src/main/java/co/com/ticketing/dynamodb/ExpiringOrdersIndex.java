package co.com.ticketing.dynamodb;

import java.util.List;
import java.util.stream.IntStream;

/**
 * Sparse index of the orders table with the reservations that are still alive. Only orders with an expiryShard
 * attribute are in it: the attribute is set when an order is reserved and removed when it leaves that state.
 * The orders are spread over a few shards, so a big release does not overload a single index partition.
 */
final class ExpiringOrdersIndex {

    static final String NAME = "expiring-orders";
    static final int SHARD_COUNT = 4;

    private ExpiringOrdersIndex() {
    }

    static String shardOf(String orderId) {
        return String.valueOf(Math.floorMod(orderId.hashCode(), SHARD_COUNT));
    }

    static List<String> shards() {
        return IntStream.range(0, SHARD_COUNT).mapToObj(String::valueOf).toList();
    }
}

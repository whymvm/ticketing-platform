package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderHistoryEntry;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.Map;

/**
 * Builds the history entry that goes inside the same transaction as each change of an order, so a transition is
 * never recorded without happening, nor happens without being recorded.
 */
final class OrderHistoryWrites {

    private static final TableSchema<OrderHistoryEntity> SCHEMA = TableSchema.fromBean(OrderHistoryEntity.class);

    private OrderHistoryWrites() {
    }

    // The history is append-only: an entry is never overwritten
    static TransactWriteItem append(String tableName, Order order) {
        OrderHistoryEntry entry = OrderHistoryEntry.of(order);
        return TransactWriteItem.builder()
                .put(put -> put
                        .tableName(tableName)
                        .item(SCHEMA.itemToMap(OrderHistoryEntity.from(entry), true))
                        .conditionExpression("attribute_not_exists(#sk)")
                        .expressionAttributeNames(Map.of("#sk", "sk")))
                .build();
    }
}

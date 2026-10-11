package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.Order;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

/**
 * Builds the write of a new order for a transaction.
 */
final class OrderWrites {

    private static final TableSchema<OrderEntity> SCHEMA = TableSchema.fromBean(OrderEntity.class);

    private OrderWrites() {
    }

    // A new order never overwrites an existing one with the same id
    static TransactWriteItem newOrder(String tableName, Order order) {
        return TransactWriteItem.builder().put(Put.builder()
                        .tableName(tableName)
                        .item(SCHEMA.itemToMap(OrderEntity.from(order), true))
                        .conditionExpression(DynamoDBExpressions.ID_NOT_EXISTS.expression())
                        .expressionAttributeNames(DynamoDBExpressions.ID_NOT_EXISTS.expressionNames())
                        .build())
                .build();
    }
}

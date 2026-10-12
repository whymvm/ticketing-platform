package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.IdempotencyRecord;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

import java.util.Map;

/**
 * Builds the idempotency record that goes inside the same transaction as a new order, so a key never points to
 * an order that does not exist and two requests with the same key never create two orders.
 */
final class IdempotencyWrites {

    private static final TableSchema<IdempotencyEntity> SCHEMA = TableSchema.fromBean(IdempotencyEntity.class);

    private IdempotencyWrites() {
    }

    // The key is taken only if nobody has it, or if its record already expired but DynamoDB did not delete it yet
    static TransactWriteItem claim(String tableName, IdempotencyRecord idempotencyRecord) {
        return TransactWriteItem.builder()
                .put(put -> put
                        .tableName(tableName)
                        .item(SCHEMA.itemToMap(IdempotencyEntity.from(idempotencyRecord), true))
                        .conditionExpression("attribute_not_exists(#key) OR #ttl < :now")
                        .expressionAttributeNames(Map.of("#key", "idempotencyKey", "#ttl", "ttl"))
                        .expressionAttributeValues(Map.of(":now",
                                AttributeValue.fromN(String.valueOf(idempotencyRecord.createdAt().getEpochSecond())))))
                .build();
    }
}

package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.gateways.IdempotencyRecordRetriever;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Reads the idempotency records from DynamoDB.
 */
@Repository
public class IdempotencyDynamoDBAdapter implements IdempotencyRecordRetriever {

    private final DynamoDbAsyncTable<IdempotencyEntity> table;

    public IdempotencyDynamoDBAdapter(DynamoDbEnhancedAsyncClient client, DynamoDBProperties properties) {
        this.table = client.table(properties.tables().idempotency(), TableSchema.fromBean(IdempotencyEntity.class));
    }

    // Strongly consistent read: a retry that arrives right after the first request must find its key
    @Override
    public Mono<IdempotencyRecord> findByKey(String key) {
        return Mono.fromFuture(() -> table.getItem(request -> request
                        .key(itemKey -> itemKey.partitionValue(key))
                        .consistentRead(true)))
                .map(IdempotencyEntity::toDomain);
    }
}

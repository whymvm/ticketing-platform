package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.gateways.OrderHistoryRetriever;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

/**
 * Reads the history of the orders from DynamoDB.
 */
@Repository
public class OrderHistoryDynamoDBAdapter implements OrderHistoryRetriever {

    private final DynamoDbAsyncTable<OrderHistoryEntity> table;

    public OrderHistoryDynamoDBAdapter(DynamoDbEnhancedAsyncClient client, DynamoDBProperties properties) {
        this.table = client.table(properties.tables().orderHistory(), TableSchema.fromBean(OrderHistoryEntity.class));
    }

    // The sort key starts with the instant, so the query returns the entries oldest first. The read is strongly
    // consistent, so the history always includes the transition that was just written
    @Override
    public Flux<OrderHistoryEntry> findByOrderId(String orderId) {
        return Flux.from(table.query(request -> request
                        .queryConditional(QueryConditional.keyEqualTo(key -> key.partitionValue(orderId)))
                        .consistentRead(true)))
                .flatMapIterable(Page::items)
                .map(OrderHistoryEntity::toDomain);
    }
}

package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.gateways.OrderCreator;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Stores orders in DynamoDB.
 */
@Repository
public class OrderDynamoDBAdapter implements OrderCreator {

    private final DynamoDbAsyncTable<OrderEntity> table;

    public OrderDynamoDBAdapter(DynamoDbEnhancedAsyncClient client, DynamoDBProperties properties) {
        this.table = client.table(properties.tables().orders(), TableSchema.fromBean(OrderEntity.class));
    }

    @Override
    public Mono<QueuedOrder> save(QueuedOrder order) {
        return Mono.fromFuture(() -> table.putItem(newItemRequest(order)))
                .onErrorMap(ConditionalCheckFailedException.class, e -> new OrderAlreadyExistsException(order.details().id()))
                .thenReturn(order);
    }

    private static PutItemEnhancedRequest<OrderEntity> newItemRequest(QueuedOrder order) {
        return PutItemEnhancedRequest.builder(OrderEntity.class)
                .item(OrderEntity.from(order))
                .conditionExpression(DynamoDBExpressions.ID_NOT_EXISTS)
                .build();
    }
}

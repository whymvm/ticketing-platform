package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import co.com.ticketing.model.order.gateways.OrderStatusUpdater;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.Map;

import static co.com.ticketing.dynamodb.DynamoDBExpressions.QUEUED_VALUE;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_IS_QUEUED;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_NAME;

/**
 * Stores, retrieves and updates orders in DynamoDB.
 */
@Repository
public class OrderDynamoDBAdapter implements OrderCreator, OrderRetriever, OrderStatusUpdater {

    private final DynamoDbAsyncTable<OrderEntity> table;
    private final DynamoDbAsyncClient client;
    private final String tableName;

    public OrderDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedClient, DynamoDbAsyncClient client,
                                DynamoDBProperties properties) {
        this.tableName = properties.tables().orders();
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderEntity.class));
        this.client = client;
    }

    @Override
    public Mono<QueuedOrder> save(QueuedOrder order) {
        return Mono.fromFuture(() -> table.putItem(newItemRequest(order)))
                .onErrorMap(ConditionalCheckFailedException.class, e -> new OrderAlreadyExistsException(order.details().id()))
                .thenReturn(order);
    }

    @Override
    public Mono<Order> findById(String id) {
        return Mono.fromFuture(() -> table.getItem(Key.builder().partitionValue(id).build()))
                .map(OrderEntity::toDomain);
    }

    @Override
    public Mono<RejectedOrder> markRejected(RejectedOrder order) {
        return Mono.fromFuture(() -> client.updateItem(rejection(order)))
                .onErrorMap(ConditionalCheckFailedException.class,
                        e -> new OrderAlreadyProcessedException(order.details().id()))
                .thenReturn(order);
    }

    // Partial update: only the status and the reason change, and only if the order is still queued
    private UpdateItemRequest rejection(RejectedOrder order) {
        return UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of("id", AttributeValue.fromS(order.details().id())))
                .updateExpression("SET #status = :rejected, #rejectionReason = :rejectionReason")
                .conditionExpression(STATUS_IS_QUEUED)
                .expressionAttributeNames(Map.of(STATUS_NAME, "status", "#rejectionReason", "rejectionReason"))
                .expressionAttributeValues(Map.of(
                        ":rejected", AttributeValue.fromS(OrderStatus.REJECTED.name()),
                        QUEUED_VALUE, AttributeValue.fromS(OrderStatus.QUEUED.name()),
                        ":rejectionReason", AttributeValue.fromS(order.rejectionReason().name())))
                .build();
    }

    private static PutItemEnhancedRequest<OrderEntity> newItemRequest(QueuedOrder order) {
        return PutItemEnhancedRequest.builder(OrderEntity.class)
                .item(OrderEntity.from(order))
                .conditionExpression(DynamoDBExpressions.ID_NOT_EXISTS)
                .build();
    }
}

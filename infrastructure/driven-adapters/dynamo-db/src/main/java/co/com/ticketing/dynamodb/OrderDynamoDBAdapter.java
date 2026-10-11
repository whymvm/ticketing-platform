package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.gateways.OrderCreator;
import co.com.ticketing.model.order.gateways.OrderRetriever;
import co.com.ticketing.model.order.gateways.OrderStatusUpdater;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
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
    private final DynamoDbAsyncIndex<OrderEntity> expiringOrders;
    private final DynamoDbAsyncClient client;
    private final String tableName;

    public OrderDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedClient, DynamoDbAsyncClient client,
                                DynamoDBProperties properties) {
        this.tableName = properties.tables().orders();
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderEntity.class));
        this.expiringOrders = table.index(ExpiringOrdersIndex.NAME);
        this.client = client;
    }

    @Override
    public Mono<QueuedOrder> save(QueuedOrder order) {
        return Mono.fromFuture(() -> table.putItem(newItemRequest(order)))
                .onErrorMap(ConditionalCheckFailedException.class, _ -> new OrderAlreadyExistsException(order.details().id()))
                .thenReturn(order);
    }

    // Strongly consistent read: the consumer reads an order right after it is saved, the confirmation reads it
    // again after losing a race, and a client reads it right after the 202. A default (eventually consistent)
    // read could miss those writes
    @Override
    public Mono<Order> findById(String id) {
        return Mono.fromFuture(() -> table.getItem(request -> request
                        .key(key -> key.partitionValue(id))
                        .consistentRead(true)))
                .map(OrderEntity::toDomain);
    }

    // Reads every shard of the sparse index in parallel. Expiration instants are stored as ISO-8601 text, so they
    // compare in time order with a margin of less than one second; the use case checks the exact instant
    @Override
    public Flux<ReservedOrder> findExpiredReservations(Instant now) {
        return Flux.fromIterable(ExpiringOrdersIndex.shards())
                .flatMap(shard -> Flux.from(expiringOrders.query(QueryConditional.sortLessThanOrEqualTo(
                        Key.builder().partitionValue(shard).sortValue(now.toString()).build()))))
                .flatMapIterable(Page::items)
                .map(OrderEntity::toDomain)
                .ofType(ReservedOrder.class);
    }

    @Override
    public Mono<RejectedOrder> markRejected(RejectedOrder order) {
        return Mono.fromFuture(() -> client.updateItem(rejection(order)))
                .onErrorMap(ConditionalCheckFailedException.class,
                        _ -> new OrderAlreadyProcessedException(order.details().id()))
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

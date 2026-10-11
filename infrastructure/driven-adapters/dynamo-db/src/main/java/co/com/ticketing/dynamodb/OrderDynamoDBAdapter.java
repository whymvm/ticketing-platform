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
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Instant;
import java.util.Map;

import static co.com.ticketing.dynamodb.DynamoDBExpressions.QUEUED_VALUE;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_IS_QUEUED;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_NAME;

/**
 * Stores, retrieves and updates orders in DynamoDB. Every write also records the transition in the order history,
 * in the same transaction.
 */
@Repository
public class OrderDynamoDBAdapter implements OrderCreator, OrderRetriever, OrderStatusUpdater {

    private static final TableSchema<OrderEntity> SCHEMA = TableSchema.fromBean(OrderEntity.class);

    // Position of the order in each transaction, also used to read its cancellation reason
    private static final int ORDER_ITEM = 0;

    private final DynamoDbAsyncTable<OrderEntity> table;
    private final DynamoDbAsyncIndex<OrderEntity> expiringOrders;
    private final DynamoDbAsyncClient client;
    private final DynamoDBProperties.Tables tables;

    public OrderDynamoDBAdapter(DynamoDbEnhancedAsyncClient enhancedClient, DynamoDbAsyncClient client,
                                DynamoDBProperties properties) {
        this.tables = properties.tables();
        this.table = enhancedClient.table(tables.orders(), SCHEMA);
        this.expiringOrders = table.index(ExpiringOrdersIndex.NAME);
        this.client = client;
    }

    @Override
    public Mono<QueuedOrder> save(QueuedOrder order) {
        return transaction(newOrder(order), order)
                .onErrorMap(OrderDynamoDBAdapter::orderConditionFailed,
                        _ -> new OrderAlreadyExistsException(order.details().id()))
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
        return transaction(rejection(order), order)
                .onErrorMap(OrderDynamoDBAdapter::orderConditionFailed,
                        _ -> new OrderAlreadyProcessedException(order.details().id()))
                .thenReturn(order);
    }

    private Mono<Void> transaction(TransactWriteItem orderChange, Order order) {
        return Mono.fromFuture(() -> client.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(orderChange, OrderHistoryWrites.record(tables.orderHistory(), order))
                        .build()))
                .then();
    }

    // A new order never overwrites an existing one with the same id
    private TransactWriteItem newOrder(QueuedOrder order) {
        return TransactWriteItem.builder().put(Put.builder()
                        .tableName(tables.orders())
                        .item(SCHEMA.itemToMap(OrderEntity.from(order), true))
                        .conditionExpression(DynamoDBExpressions.ID_NOT_EXISTS.expression())
                        .expressionAttributeNames(DynamoDBExpressions.ID_NOT_EXISTS.expressionNames())
                        .build())
                .build();
    }

    // Partial update: only the status, the reason and the update time change, and only if the order is still queued
    private TransactWriteItem rejection(RejectedOrder order) {
        return TransactWriteItem.builder().update(Update.builder()
                        .tableName(tables.orders())
                        .key(Map.of("id", AttributeValue.fromS(order.details().id())))
                        .updateExpression("SET #status = :rejected, #rejectionReason = :rejectionReason, "
                                + "#updatedAt = :updatedAt")
                        .conditionExpression(STATUS_IS_QUEUED)
                        .expressionAttributeNames(Map.of(STATUS_NAME, "status", "#rejectionReason", "rejectionReason",
                                "#updatedAt", "updatedAt"))
                        .expressionAttributeValues(Map.of(
                                ":rejected", AttributeValue.fromS(OrderStatus.REJECTED.name()),
                                QUEUED_VALUE, AttributeValue.fromS(OrderStatus.QUEUED.name()),
                                ":rejectionReason", AttributeValue.fromS(order.rejectionReason().name()),
                                ":updatedAt", AttributeValue.fromS(order.updatedAt().toString())))
                        .build())
                .build();
    }

    private static boolean orderConditionFailed(Throwable error) {
        return error instanceof TransactionCanceledException cancelled
                && TransactionCancellation.failedCondition(cancelled, ORDER_ITEM);
    }
}

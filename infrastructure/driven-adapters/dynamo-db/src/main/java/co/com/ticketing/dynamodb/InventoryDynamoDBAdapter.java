package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static co.com.ticketing.dynamodb.DynamoDBExpressions.QUEUED_VALUE;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_IS_QUEUED;
import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_NAME;

/**
 * Updates the ticket inventory of events in DynamoDB.
 */
@Repository
public class InventoryDynamoDBAdapter implements InventoryUpdater {

    // Position of each item in the transaction, also used to read its cancellation reason
    private static final int EVENT_ITEM = 0;
    private static final int ORDER_ITEM = 1;

    private static final String CONDITIONAL_CHECK_FAILED = "ConditionalCheckFailed";
    private static final String TRANSACTION_CONFLICT = "TransactionConflict";

    private static final Map<String, String> AVAILABLE_AND_RESERVED =
            Map.of("#available", "available", "#reserved", "reserved");
    private static final Map<String, String> RESERVED_AND_SOLD = Map.of("#reserved", "reserved", "#sold", "sold");

    private final DynamoDbAsyncClient client;
    private final DynamoDBProperties.Tables tables;

    public InventoryDynamoDBAdapter(DynamoDbAsyncClient client, DynamoDBProperties properties) {
        this.client = client;
        this.tables = properties.tables();
    }

    @Override
    public Mono<ReservedOrder> reserveTickets(ReservedOrder order) {
        OrderDetails details = order.details();
        return transaction(holdTickets(details), markReserved(order))
                .onErrorMap(TransactionCanceledException.class, error -> cancellationCause(error, details,
                        () -> new TicketsSoldOutException(details.eventId(), details.quantity())))
                .thenReturn(order);
    }

    @Override
    public Mono<ExpiredOrder> releaseTickets(ExpiredOrder order) {
        OrderDetails details = order.details();
        return transaction(returnTickets(details), markExpired(order))
                .onErrorMap(TransactionCanceledException.class, error -> cancellationCause(error, details,
                        () -> fewerReservedTickets(details)))
                .thenReturn(order);
    }

    @Override
    public Mono<SoldOrder> sellTickets(SoldOrder order) {
        OrderDetails details = order.details();
        return transaction(sellReservedTickets(details), markSold(order))
                .onErrorMap(TransactionCanceledException.class, error -> cancellationCause(error, details,
                        () -> fewerReservedTickets(details)))
                .thenReturn(order);
    }

    private Mono<Void> transaction(TransactWriteItem event, TransactWriteItem order) {
        return Mono.fromFuture(() -> client.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(event, order)
                        .build()))
                .then();
    }

    // Partial update of the event: only its counters change, and only if there are enough tickets
    private TransactWriteItem holdTickets(OrderDetails details) {
        return eventUpdate(details, AVAILABLE_AND_RESERVED,
                "SET #available = #available - :quantity, #reserved = #reserved + :quantity",
                "#available >= :quantity");
    }

    // Partial update of the event: the tickets go back to available, and never below zero reserved tickets
    private TransactWriteItem returnTickets(OrderDetails details) {
        return eventUpdate(details, AVAILABLE_AND_RESERVED,
                "SET #available = #available + :quantity, #reserved = #reserved - :quantity",
                "#reserved >= :quantity");
    }

    // Partial update of the event: the reserved tickets become sold
    private TransactWriteItem sellReservedTickets(OrderDetails details) {
        return eventUpdate(details, RESERVED_AND_SOLD,
                "SET #reserved = #reserved - :quantity, #sold = #sold + :quantity",
                "#reserved >= :quantity");
    }

    private TransactWriteItem eventUpdate(OrderDetails details, Map<String, String> counters, String updateExpression,
                                          String condition) {
        return TransactWriteItem.builder().update(Update.builder()
                        .tableName(tables.events())
                        .key(Map.of("id", AttributeValue.fromS(details.eventId())))
                        .updateExpression(updateExpression)
                        .conditionExpression(condition)
                        .expressionAttributeNames(counters)
                        .expressionAttributeValues(Map.of(":quantity",
                                AttributeValue.fromN(String.valueOf(details.quantity()))))
                        .build())
                .build();
    }

    // Partial update of the order: its status and expiration change, and it enters the expiring orders index
    private TransactWriteItem markReserved(ReservedOrder order) {
        String id = order.details().id();
        return TransactWriteItem.builder().update(Update.builder()
                        .tableName(tables.orders())
                        .key(Map.of("id", AttributeValue.fromS(id)))
                        .updateExpression("SET #status = :reserved, #expiresAt = :expiresAt, #expiryShard = :expiryShard")
                        .conditionExpression(STATUS_IS_QUEUED)
                        .expressionAttributeNames(Map.of(STATUS_NAME, "status", "#expiresAt", "expiresAt",
                                "#expiryShard", "expiryShard"))
                        .expressionAttributeValues(Map.of(
                                ":reserved", AttributeValue.fromS(OrderStatus.RESERVED.name()),
                                QUEUED_VALUE, AttributeValue.fromS(OrderStatus.QUEUED.name()),
                                ":expiresAt", AttributeValue.fromS(order.expiresAt().toString()),
                                ":expiryShard", AttributeValue.fromS(ExpiringOrdersIndex.shardOf(id))))
                        .build())
                .build();
    }

    private TransactWriteItem markExpired(ExpiredOrder order) {
        return leaveReservation(order.details(), OrderStatus.EXPIRED);
    }

    private TransactWriteItem markSold(SoldOrder order) {
        return leaveReservation(order.details(), OrderStatus.SOLD);
    }

    // Partial update of a reserved order: its status changes and it leaves the expiring orders index
    private TransactWriteItem leaveReservation(OrderDetails details, OrderStatus newStatus) {
        return TransactWriteItem.builder().update(Update.builder()
                        .tableName(tables.orders())
                        .key(Map.of("id", AttributeValue.fromS(details.id())))
                        .updateExpression("SET #status = :newStatus REMOVE #expiryShard")
                        .conditionExpression("#status = :reserved")
                        .expressionAttributeNames(Map.of(STATUS_NAME, "status", "#expiryShard", "expiryShard"))
                        .expressionAttributeValues(Map.of(
                                ":newStatus", AttributeValue.fromS(newStatus.name()),
                                ":reserved", AttributeValue.fromS(OrderStatus.RESERVED.name())))
                        .build())
                .build();
    }

    private static InconsistentInventoryException fewerReservedTickets(OrderDetails details) {
        return new InconsistentInventoryException("event " + details.eventId()
                + " has fewer than " + details.quantity() + " reserved tickets");
    }

    // The order check goes first: if the order was already processed, the event counters do not matter
    private static Throwable cancellationCause(TransactionCanceledException error, OrderDetails details,
                                               Supplier<RuntimeException> eventConditionFailed) {
        List<String> codes = error.cancellationReasons().stream().map(CancellationReason::code).toList();
        if (hasCode(codes, ORDER_ITEM, CONDITIONAL_CHECK_FAILED)) {
            return new OrderAlreadyProcessedException(details.id());
        }
        if (hasCode(codes, EVENT_ITEM, CONDITIONAL_CHECK_FAILED)) {
            return eventConditionFailed.get();
        }
        if (codes.contains(TRANSACTION_CONFLICT)) {
            return new ConcurrencyConflictException(details.id());
        }
        return error;
    }

    private static boolean hasCode(List<String> codes, int item, String code) {
        return codes.size() > item && code.equals(codes.get(item));
    }
}

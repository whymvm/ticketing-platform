package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.ExpirationReason;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import co.com.ticketing.model.order.gateways.ComplimentaryIssuer;
import co.com.ticketing.model.order.gateways.InventoryUpdater;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static co.com.ticketing.dynamodb.DynamoDBExpressions.STATUS_NAME;

/**
 * Updates the ticket inventory of events in DynamoDB. Each change moves tickets between two counters of the event,
 * changes the order and records the transition in its history, all in one transaction.
 */
@Repository
public class InventoryDynamoDBAdapter implements InventoryUpdater, ComplimentaryIssuer {

    // Position of each item in the transaction, also used to read its cancellation reason
    private static final int EVENT_ITEM = 0;
    private static final int ORDER_ITEM = 1;

    private static final String AVAILABLE = "available";
    private static final String RESERVED = "reserved";
    private static final String PENDING_CONFIRMATION = "pendingConfirmation";
    private static final String SOLD = "sold";
    private static final String COMPLIMENTARY = "complimentary";

    // Position of the idempotency record in the complimentary transaction
    private static final int IDEMPOTENCY_ITEM = 3;

    private final DynamoDbAsyncClient client;
    private final DynamoDBProperties.Tables tables;

    public InventoryDynamoDBAdapter(DynamoDbAsyncClient client, DynamoDBProperties properties) {
        this.client = client;
        this.tables = properties.tables();
    }

    @Override
    public Mono<ReservedOrder> reserveTickets(ReservedOrder order) {
        OrderDetails details = order.details();
        return transaction(moveTickets(details, AVAILABLE, RESERVED), markReserved(order), order,
                () -> new TicketsSoldOutException(details.eventId(), details.quantity()));
    }

    @Override
    public Mono<PendingConfirmationOrder> holdForPayment(PendingConfirmationOrder order) {
        return transaction(moveTickets(order.details(), RESERVED, PENDING_CONFIRMATION), markWaitingForPayment(order),
                order, () -> fewerTicketsThanExpected(order.details(), RESERVED));
    }

    @Override
    public Mono<SoldOrder> sellTickets(SoldOrder order) {
        return transaction(moveTickets(order.details(), PENDING_CONFIRMATION, SOLD),
                leaveHold(order, OrderStatus.PENDING_CONFIRMATION, Map.of()), order,
                () -> fewerTicketsThanExpected(order.details(), PENDING_CONFIRMATION));
    }

    // An expired order gives back the tickets it was holding: reserved ones, or ones waiting for the payment
    @Override
    public Mono<ExpiredOrder> releaseTickets(ExpiredOrder order) {
        boolean paymentTimedOut = order.expirationReason() == ExpirationReason.PAYMENT_TIMEOUT;
        String heldIn = paymentTimedOut ? PENDING_CONFIRMATION : RESERVED;
        OrderStatus expiredFrom = paymentTimedOut ? OrderStatus.PENDING_CONFIRMATION : OrderStatus.RESERVED;
        return transaction(moveTickets(order.details(), heldIn, AVAILABLE),
                leaveHold(order, expiredFrom, Map.of("expirationReason", order.expirationReason().name())), order,
                () -> fewerTicketsThanExpected(order.details(), heldIn));
    }

    @Override
    public Mono<RejectedOrder> releaseTickets(RejectedOrder order) {
        return transaction(moveTickets(order.details(), PENDING_CONFIRMATION, AVAILABLE),
                leaveHold(order, OrderStatus.PENDING_CONFIRMATION,
                        Map.of("rejectionReason", order.rejectionReason().name())), order,
                () -> fewerTicketsThanExpected(order.details(), PENDING_CONFIRMATION));
    }

    // The tickets, the new order, its history entry and the idempotency key are written together, or none of them
    @Override
    public Mono<ComplimentaryOrder> issue(ComplimentaryOrder order, IdempotencyRecord idempotencyRecord) {
        OrderDetails details = order.details();
        return Mono.fromFuture(() -> client.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(moveTickets(details, AVAILABLE, COMPLIMENTARY),
                                OrderWrites.newOrder(tables.orders(), order),
                                OrderHistoryWrites.append(tables.orderHistory(), order),
                                IdempotencyWrites.claim(tables.idempotency(), idempotencyRecord))
                        .build()))
                .onErrorMap(TransactionCanceledException.class, error -> issueCancellationCause(error, details, idempotencyRecord))
                .thenReturn(order);
    }

    private static Throwable issueCancellationCause(TransactionCanceledException error, OrderDetails details,
                                                    IdempotencyRecord idempotencyRecord) {
        if (TransactionCancellation.failedCondition(error, IDEMPOTENCY_ITEM)) {
            return new DuplicateRequestException(idempotencyRecord.key());
        }
        if (TransactionCancellation.failedCondition(error, EVENT_ITEM)) {
            return new TicketsSoldOutException(details.eventId(), details.quantity());
        }
        if (TransactionCancellation.failedCondition(error, ORDER_ITEM)) {
            return new OrderAlreadyExistsException(details.id());
        }
        if (TransactionCancellation.conflicted(error)) {
            return new ConcurrencyConflictException(details.id());
        }
        return error;
    }

    private <T extends Order> Mono<T> transaction(TransactWriteItem event, TransactWriteItem orderChange, T order,
                                                 Supplier<RuntimeException> eventConditionFailed) {
        return Mono.fromFuture(() -> client.transactWriteItems(TransactWriteItemsRequest.builder()
                        .transactItems(event, orderChange, OrderHistoryWrites.append(tables.orderHistory(), order))
                        .build()))
                .onErrorMap(TransactionCanceledException.class,
                        error -> cancellationCause(error, order.details(), eventConditionFailed))
                .thenReturn(order);
    }

    // Partial update of the event: only two counters change, and only if the source counter has enough tickets
    private TransactWriteItem moveTickets(OrderDetails details, String from, String to) {
        return TransactWriteItem.builder()
                .update(update -> update
                        .tableName(tables.events())
                        .key(Map.of("id", AttributeValue.fromS(details.eventId())))
                        .updateExpression("SET #from = #from - :quantity, #to = #to + :quantity")
                        .conditionExpression("#from >= :quantity")
                        .expressionAttributeNames(Map.of("#from", from, "#to", to))
                        .expressionAttributeValues(Map.of(":quantity",
                                AttributeValue.fromN(String.valueOf(details.quantity())))))
                .build();
    }

    // The order enters the expiring orders index with the reservation deadline
    private TransactWriteItem markReserved(ReservedOrder order) {
        String id = order.details().id();
        return orderUpdate(order, OrderStatus.QUEUED,
                "SET #status = :newStatus, #expiresAt = :expiresAt, #expiryShard = :expiryShard, #updatedAt = :updatedAt",
                Map.of("#expiresAt", "expiresAt", "#expiryShard", "expiryShard"),
                Map.of(":expiresAt", AttributeValue.fromS(order.expiresAt().toString()),
                        ":expiryShard", AttributeValue.fromS(ExpiringOrdersIndex.shardOf(id))));
    }

    // The order stays in the expiring orders index, now with the payment deadline
    private TransactWriteItem markWaitingForPayment(PendingConfirmationOrder order) {
        return orderUpdate(order, OrderStatus.RESERVED,
                "SET #status = :newStatus, #expiresAt = :expiresAt, #updatedAt = :updatedAt",
                Map.of("#expiresAt", "expiresAt"),
                Map.of(":expiresAt", AttributeValue.fromS(order.expiresAt().toString())));
    }

    // The order stops holding tickets, so it leaves the expiring orders index; extra attributes record why
    private TransactWriteItem leaveHold(Order order, OrderStatus expected, Map<String, String> extraAttributes) {
        StringBuilder set = new StringBuilder("SET #status = :newStatus, #updatedAt = :updatedAt");
        Map<String, String> names = new HashMap<>(Map.of("#expiryShard", "expiryShard"));
        Map<String, AttributeValue> values = new HashMap<>();
        extraAttributes.forEach((attribute, value) -> {
            set.append(", #").append(attribute).append(" = :").append(attribute);
            names.put("#" + attribute, attribute);
            values.put(":" + attribute, AttributeValue.fromS(value));
        });
        return orderUpdate(order, expected, set + " REMOVE #expiryShard", names, values);
    }

    // Partial update of the order, only if it is still in the expected state
    private TransactWriteItem orderUpdate(Order order, OrderStatus expected, String updateExpression,
                                          Map<String, String> names, Map<String, AttributeValue> values) {
        Map<String, String> allNames = new HashMap<>(names);
        allNames.put(STATUS_NAME, "status");
        allNames.put("#updatedAt", "updatedAt");
        Map<String, AttributeValue> allValues = new HashMap<>(values);
        allValues.put(":newStatus", AttributeValue.fromS(order.status().name()));
        allValues.put(":expected", AttributeValue.fromS(expected.name()));
        allValues.put(":updatedAt", AttributeValue.fromS(order.updatedAt().toString()));
        return TransactWriteItem.builder()
                .update(update -> update
                        .tableName(tables.orders())
                        .key(Map.of("id", AttributeValue.fromS(order.details().id())))
                        .updateExpression(updateExpression)
                        .conditionExpression(STATUS_NAME + " = :expected")
                        .expressionAttributeNames(allNames)
                        .expressionAttributeValues(allValues))
                .build();
    }

    private static InconsistentInventoryException fewerTicketsThanExpected(OrderDetails details, String counter) {
        return new InconsistentInventoryException("event " + details.eventId() + " has fewer than "
                + details.quantity() + " " + counter + " tickets");
    }

    // The order check goes first: if the order was already processed, the event counters do not matter
    private static Throwable cancellationCause(TransactionCanceledException error, OrderDetails details,
                                               Supplier<RuntimeException> eventConditionFailed) {
        if (TransactionCancellation.failedCondition(error, ORDER_ITEM)) {
            return new OrderAlreadyProcessedException(details.id());
        }
        if (TransactionCancellation.failedCondition(error, EVENT_ITEM)) {
            return eventConditionFailed.get();
        }
        if (TransactionCancellation.conflicted(error)) {
            return new ConcurrencyConflictException(details.id());
        }
        return error;
    }
}

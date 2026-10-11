package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ComplimentaryOrder;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewComplimentary;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.PendingConfirmationOrder;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDynamoDBAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final Instant CONFIRMED_AT = NOW.plus(Duration.ofMinutes(4));
    private static final Instant LATER = CONFIRMED_AT.plus(Duration.ofMinutes(1));
    private static final ReservedOrder RESERVED = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW))
            .reserve(NOW, Duration.ofMinutes(10));
    private static final PendingConfirmationOrder PENDING = RESERVED.confirm(CONFIRMED_AT);
    private static final ComplimentaryOrder COMPLIMENTARY = ComplimentaryOrder.issue("o-1", "e-1",
            new NewComplimentary("guest-001", 2), NOW);
    private static final IdempotencyRecord COMPLIMENTARY_RECORD =
            IdempotencyRecord.of(new IdempotencyKey("organizer", "comp-0001"), COMPLIMENTARY);

    @Mock
    private DynamoDbAsyncClient client;

    private InventoryDynamoDBAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InventoryDynamoDBAdapter(client, new DynamoDBProperties("us-east-1", null,
                new DynamoDBProperties.Tables("local-ticketing-events", "local-ticketing-orders",
                        "local-ticketing-order-history", "local-ticketing-idempotency")));
    }

    @Test
    void reserveTicketsMovesThemFromAvailableToReservedAndMarksTheOrder() {
        allowTransactions();

        StepVerifier.create(adapter.reserveTickets(RESERVED)).expectNext(RESERVED).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertThat(request.transactItems()).hasSize(3);
        assertTicketsMove(request.transactItems().get(0).update(), "available", "reserved");

        Update order = request.transactItems().get(1).update();
        assertThat(order.tableName()).isEqualTo("local-ticketing-orders");
        assertThat(order.key()).containsEntry("id", AttributeValue.fromS("o-1"));
        assertThat(order.updateExpression()).isEqualTo(
                "SET #status = :newStatus, #expiresAt = :expiresAt, #expiryShard = :expiryShard, #updatedAt = :updatedAt");
        assertThat(order.conditionExpression()).isEqualTo("#status = :expected");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":newStatus", AttributeValue.fromS("RESERVED"))
                .containsEntry(":expected", AttributeValue.fromS("QUEUED"))
                .containsEntry(":expiresAt", AttributeValue.fromS("2026-10-10T12:10:00Z"))
                .containsEntry(":expiryShard", AttributeValue.fromS(ExpiringOrdersIndex.shardOf("o-1")))
                .containsEntry(":updatedAt", AttributeValue.fromS("2026-10-10T12:00:00Z"));

        assertHistoryEntry(request, "QUEUED", "RESERVED", "TICKETS_RESERVED");
    }

    @Test
    void holdForPaymentMovesTheTicketsToPendingConfirmationAndKeepsTheOrderInTheIndex() {
        allowTransactions();

        StepVerifier.create(adapter.holdForPayment(PENDING)).expectNext(PENDING).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertTicketsMove(request.transactItems().get(0).update(), "reserved", "pendingConfirmation");

        Update order = request.transactItems().get(1).update();
        assertThat(order.updateExpression())
                .isEqualTo("SET #status = :newStatus, #expiresAt = :expiresAt, #updatedAt = :updatedAt");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":newStatus", AttributeValue.fromS("PENDING_CONFIRMATION"))
                .containsEntry(":expected", AttributeValue.fromS("RESERVED"))
                .containsEntry(":expiresAt", AttributeValue.fromS("2026-10-10T12:09:00Z"));

        assertHistoryEntry(request, "RESERVED", "PENDING_CONFIRMATION", "ORDER_CONFIRMED");
    }

    @Test
    void sellTicketsMovesThemFromPendingConfirmationToSoldAndTakesTheOrderOutOfTheIndex() {
        SoldOrder sold = PENDING.markPaid(LATER);
        allowTransactions();

        StepVerifier.create(adapter.sellTickets(sold)).expectNext(sold).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertTicketsMove(request.transactItems().get(0).update(), "pendingConfirmation", "sold");

        Update order = request.transactItems().get(1).update();
        assertThat(order.updateExpression())
                .isEqualTo("SET #status = :newStatus, #updatedAt = :updatedAt REMOVE #expiryShard");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":newStatus", AttributeValue.fromS("SOLD"))
                .containsEntry(":expected", AttributeValue.fromS("PENDING_CONFIRMATION"));

        assertHistoryEntry(request, "PENDING_CONFIRMATION", "SOLD", "PAYMENT_APPROVED");
    }

    @Test
    void releaseTicketsOfAnExpiredReservationGivesBackTheReservedTickets() {
        ExpiredOrder expired = RESERVED.expire(LATER);
        allowTransactions();

        StepVerifier.create(adapter.releaseTickets(expired)).expectNext(expired).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertTicketsMove(request.transactItems().get(0).update(), "reserved", "available");

        Update order = request.transactItems().get(1).update();
        assertThat(order.updateExpression()).isEqualTo(
                "SET #status = :newStatus, #updatedAt = :updatedAt, #expirationReason = :expirationReason REMOVE #expiryShard");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":expected", AttributeValue.fromS("RESERVED"))
                .containsEntry(":expirationReason", AttributeValue.fromS("RESERVATION_TIMEOUT"));

        assertHistoryEntry(request, "RESERVED", "EXPIRED", "RESERVATION_EXPIRED");
    }

    @Test
    void releaseTicketsOfAnExpiredPaymentGivesBackTheTicketsWaitingForIt() {
        ExpiredOrder expired = PENDING.expire(LATER);
        allowTransactions();

        StepVerifier.create(adapter.releaseTickets(expired)).expectNext(expired).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertTicketsMove(request.transactItems().get(0).update(), "pendingConfirmation", "available");
        assertThat(request.transactItems().get(1).update().expressionAttributeValues())
                .containsEntry(":expected", AttributeValue.fromS("PENDING_CONFIRMATION"))
                .containsEntry(":expirationReason", AttributeValue.fromS("PAYMENT_TIMEOUT"));

        assertHistoryEntry(request, "PENDING_CONFIRMATION", "EXPIRED", "PAYMENT_TIMEOUT");
    }

    @Test
    void releaseTicketsOfADeclinedPaymentGivesThemBackAndRejectsTheOrder() {
        RejectedOrder declined = PENDING.declinePayment(LATER);
        allowTransactions();

        StepVerifier.create(adapter.releaseTickets(declined)).expectNext(declined).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertTicketsMove(request.transactItems().get(0).update(), "pendingConfirmation", "available");
        assertThat(request.transactItems().get(1).update().expressionAttributeValues())
                .containsEntry(":newStatus", AttributeValue.fromS("REJECTED"))
                .containsEntry(":rejectionReason", AttributeValue.fromS("PAYMENT_DECLINED"));

        assertHistoryEntry(request, "PENDING_CONFIRMATION", "REJECTED", "PAYMENT_DECLINED");
    }

    @Test
    void reserveTicketsFailsWithTicketsSoldOutWhenTheEventConditionFails() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.reserveTickets(RESERVED))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(TicketsSoldOutException.class)
                        .hasMessage("event e-1 does not have 2 tickets available"))
                .verify();
    }

    @Test
    void otherMovesFailWithInconsistentInventoryWhenTheEventHasTooFewTickets() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.sellTickets(PENDING.markPaid(LATER)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InconsistentInventoryException.class)
                        .hasMessage("inconsistent inventory: event e-1 has fewer than 2 pendingConfirmation tickets"))
                .verify();
    }

    @Test
    void failsWithOrderAlreadyProcessedWhenTheOrderIsNoLongerInTheExpectedState() {
        cancelWith("ConditionalCheckFailed", "ConditionalCheckFailed");

        StepVerifier.create(adapter.holdForPayment(PENDING))
                .expectError(OrderAlreadyProcessedException.class)
                .verify();
    }

    @Test
    void failsWithConcurrencyConflictWhenAnotherTransactionGotInTheWay() {
        cancelWith("TransactionConflict", "None");

        StepVerifier.create(adapter.reserveTickets(RESERVED))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }

    @Test
    void keepsOtherCancellations() {
        cancelWith();

        StepVerifier.create(adapter.reserveTickets(RESERVED))
                .expectError(TransactionCanceledException.class)
                .verify();
    }

    @Test
    void issueWritesTheTicketsTheOrderItsHistoryAndTheKeyInOneTransaction() {
        allowTransactions();

        StepVerifier.create(adapter.issue(COMPLIMENTARY, COMPLIMENTARY_RECORD)).expectNext(COMPLIMENTARY).verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertThat(request.transactItems()).hasSize(4);
        assertTicketsMove(request.transactItems().get(0).update(), "available", "complimentary");

        Put order = request.transactItems().get(1).put();
        assertThat(order.tableName()).isEqualTo("local-ticketing-orders");
        assertThat(order.conditionExpression()).isEqualTo(DynamoDBExpressions.ID_NOT_EXISTS.expression());
        assertThat(order.item())
                .containsEntry("id", AttributeValue.fromS("o-1"))
                .containsEntry("customerId", AttributeValue.fromS("guest-001"))
                .containsEntry("status", AttributeValue.fromS("COMPLIMENTARY"))
                .doesNotContainKey("expiryShard");

        Put history = request.transactItems().get(2).put();
        assertThat(history.tableName()).isEqualTo("local-ticketing-order-history");
        assertThat(history.item())
                .containsEntry("toStatus", AttributeValue.fromS("COMPLIMENTARY"))
                .containsEntry("reason", AttributeValue.fromS("COMPLIMENTARY_ISSUED"))
                .doesNotContainKey("fromStatus");

        Put key = request.transactItems().get(3).put();
        assertThat(key.tableName()).isEqualTo("local-ticketing-idempotency");
        assertThat(key.item()).containsEntry("idempotencyKey", AttributeValue.fromS("organizer#comp-0001"));
    }

    static Stream<Arguments> issueCancellations() {
        return Stream.of(
                Arguments.of(new String[]{"None", "None", "None", "ConditionalCheckFailed"},
                        DuplicateRequestException.class),
                Arguments.of(new String[]{"ConditionalCheckFailed", "None", "None", "None"},
                        TicketsSoldOutException.class),
                Arguments.of(new String[]{"None", "ConditionalCheckFailed", "None", "None"},
                        OrderAlreadyExistsException.class),
                Arguments.of(new String[]{"TransactionConflict", "None", "None", "None"},
                        ConcurrencyConflictException.class),
                Arguments.of(new String[]{}, TransactionCanceledException.class));
    }

    // The key goes first: a retry of the same request must be answered with the first order, even if sold out now
    @ParameterizedTest
    @MethodSource("issueCancellations")
    void issueTranslatesEachCancellationReason(String[] codes, Class<? extends Throwable> expected) {
        cancelWith(codes);

        StepVerifier.create(adapter.issue(COMPLIMENTARY, COMPLIMENTARY_RECORD))
                .expectError(expected)
                .verify();
    }

    // Every move takes tickets from one counter of the event and adds them to another, only if there are enough
    private static void assertTicketsMove(Update event, String from, String to) {
        assertThat(event.tableName()).isEqualTo("local-ticketing-events");
        assertThat(event.key()).containsEntry("id", AttributeValue.fromS("e-1"));
        assertThat(event.updateExpression()).isEqualTo("SET #from = #from - :quantity, #to = #to + :quantity");
        assertThat(event.conditionExpression()).isEqualTo("#from >= :quantity");
        assertThat(event.expressionAttributeNames()).containsEntry("#from", from).containsEntry("#to", to);
        assertThat(event.expressionAttributeValues()).containsEntry(":quantity", AttributeValue.fromN("2"));
    }

    // The third item of every transaction appends the transition to the order history, and never overwrites one
    private static void assertHistoryEntry(TransactWriteItemsRequest request, String from, String to, String reason) {
        Put history = request.transactItems().get(2).put();
        assertThat(history.tableName()).isEqualTo("local-ticketing-order-history");
        assertThat(history.conditionExpression()).isEqualTo("attribute_not_exists(#sk)");
        assertThat(history.item())
                .containsEntry("orderId", AttributeValue.fromS("o-1"))
                .containsEntry("fromStatus", AttributeValue.fromS(from))
                .containsEntry("toStatus", AttributeValue.fromS(to))
                .containsEntry("reason", AttributeValue.fromS(reason));
        assertThat(history.item().get("sk").s()).endsWith("#" + to);
    }

    private void allowTransactions() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    }

    private TransactWriteItemsRequest capturedTransaction() {
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(captor.capture());
        return captor.getValue();
    }

    private void cancelWith(String... codes) {
        TransactionCanceledException error = TransactionCanceledException.builder()
                .message("Transaction cancelled")
                .cancellationReasons(Arrays.stream(codes)
                        .map(code -> CancellationReason.builder().code(code).build())
                        .toList())
                .build();
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
    }
}

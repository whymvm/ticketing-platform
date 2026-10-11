package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.ConcurrencyConflictException;
import co.com.ticketing.model.exception.InconsistentInventoryException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.exception.TicketsSoldOutException;
import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.ReservedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import software.amazon.awssdk.services.dynamodb.model.Update;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryDynamoDBAdapterTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");
    private static final ReservedOrder ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, NOW))
            .reserve(NOW, Duration.ofMinutes(10));

    @Mock
    private DynamoDbAsyncClient client;

    private InventoryDynamoDBAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new InventoryDynamoDBAdapter(client, new DynamoDBProperties("us-east-1", null,
                new DynamoDBProperties.Tables("local-ticketing-events", "local-ticketing-orders")));
    }

    @Test
    void reserveTicketsHoldsTheTicketsAndMarksTheOrderInOneTransaction() {
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.reserveTickets(ORDER))
                .expectNext(ORDER)
                .verifyComplete();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(captor.capture());
        assertThat(captor.getValue().transactItems()).hasSize(2);

        Update event = captor.getValue().transactItems().get(0).update();
        assertThat(event.tableName()).isEqualTo("local-ticketing-events");
        assertThat(event.key()).containsEntry("id", AttributeValue.fromS("e-1"));
        assertThat(event.updateExpression())
                .isEqualTo("SET #available = #available - :quantity, #reserved = #reserved + :quantity");
        assertThat(event.conditionExpression()).isEqualTo("#available >= :quantity");
        assertThat(event.expressionAttributeValues()).containsEntry(":quantity", AttributeValue.fromN("2"));

        Update order = captor.getValue().transactItems().get(1).update();
        assertThat(order.tableName()).isEqualTo("local-ticketing-orders");
        assertThat(order.key()).containsEntry("id", AttributeValue.fromS("o-1"));
        assertThat(order.updateExpression())
                .isEqualTo("SET #status = :reserved, #expiresAt = :expiresAt, #expiryShard = :expiryShard");
        assertThat(order.conditionExpression()).isEqualTo("#status = :queued");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":reserved", AttributeValue.fromS("RESERVED"))
                .containsEntry(":queued", AttributeValue.fromS("QUEUED"))
                .containsEntry(":expiresAt", AttributeValue.fromS("2026-10-10T12:10:00Z"))
                .containsEntry(":expiryShard", AttributeValue.fromS(ExpiringOrdersIndex.shardOf("o-1")));
    }

    @Test
    void releaseTicketsReturnsTheTicketsAndMarksTheOrderInOneTransaction() {
        ExpiredOrder expired = ORDER.expire();
        when(client.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));

        StepVerifier.create(adapter.releaseTickets(expired))
                .expectNext(expired)
                .verifyComplete();

        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(client).transactWriteItems(captor.capture());

        Update event = captor.getValue().transactItems().get(0).update();
        assertThat(event.key()).containsEntry("id", AttributeValue.fromS("e-1"));
        assertThat(event.updateExpression())
                .isEqualTo("SET #available = #available + :quantity, #reserved = #reserved - :quantity");
        assertThat(event.conditionExpression()).isEqualTo("#reserved >= :quantity");

        Update order = captor.getValue().transactItems().get(1).update();
        assertThat(order.key()).containsEntry("id", AttributeValue.fromS("o-1"));
        assertThat(order.updateExpression()).isEqualTo("SET #status = :expired REMOVE #expiryShard");
        assertThat(order.conditionExpression()).isEqualTo("#status = :reserved");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":expired", AttributeValue.fromS("EXPIRED"))
                .containsEntry(":reserved", AttributeValue.fromS("RESERVED"));
    }

    @Test
    void releaseTicketsFailsWithOrderAlreadyProcessedWhenTheOrderIsNoLongerReserved() {
        cancelWith("None", "ConditionalCheckFailed");

        StepVerifier.create(adapter.releaseTickets(ORDER.expire()))
                .expectError(OrderAlreadyProcessedException.class)
                .verify();
    }

    @Test
    void releaseTicketsFailsWithInconsistentInventoryWhenTheEventHasTooFewReservedTickets() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.releaseTickets(ORDER.expire()))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InconsistentInventoryException.class)
                        .hasMessage("inconsistent inventory: event e-1 has fewer than 2 reserved tickets"))
                .verify();
    }

    @Test
    void reserveTicketsFailsWithTicketsSoldOutWhenTheEventConditionFails() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.reserveTickets(ORDER))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(TicketsSoldOutException.class)
                        .hasMessage("event e-1 does not have 2 tickets available"))
                .verify();
    }

    @Test
    void reserveTicketsFailsWithOrderAlreadyProcessedWhenTheOrderIsNoLongerQueued() {
        cancelWith("ConditionalCheckFailed", "ConditionalCheckFailed");

        StepVerifier.create(adapter.reserveTickets(ORDER))
                .expectError(OrderAlreadyProcessedException.class)
                .verify();
    }

    @Test
    void reserveTicketsFailsWithConcurrencyConflictWhenAnotherTransactionGotInTheWay() {
        cancelWith("TransactionConflict", "None");

        StepVerifier.create(adapter.reserveTickets(ORDER))
                .expectError(ConcurrencyConflictException.class)
                .verify();
    }

    @Test
    void reserveTicketsKeepsOtherCancellations() {
        cancelWith();

        StepVerifier.create(adapter.reserveTickets(ORDER))
                .expectError(TransactionCanceledException.class)
                .verify();
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

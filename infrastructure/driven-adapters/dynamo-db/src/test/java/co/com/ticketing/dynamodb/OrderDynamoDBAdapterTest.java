package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.DuplicateRequestException;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDynamoDBAdapterTest {

    private static final String TABLE = "local-ticketing-orders";
    private static final String HISTORY_TABLE = "local-ticketing-order-history";
    private static final String IDEMPOTENCY_TABLE = "local-ticketing-idempotency";
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");
    private static final QueuedOrder ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, CREATED_AT));
    private static final IdempotencyRecord RECORD = IdempotencyRecord.of(new IdempotencyKey("c-1", "key-0001"), ORDER);

    @Mock
    private DynamoDbEnhancedAsyncClient client;

    @Mock
    private DynamoDbAsyncClient lowLevelClient;

    @Mock
    private DynamoDbAsyncTable<OrderEntity> table;

    @Mock
    private DynamoDbAsyncIndex<OrderEntity> expiringOrders;

    private OrderDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.table(eq(TABLE), any(TableSchema.class))).thenReturn(table);
        when(table.index("expiring-orders")).thenReturn(expiringOrders);
        adapter = new OrderDynamoDBAdapter(client, lowLevelClient,
                new DynamoDBProperties("us-east-1", null, new DynamoDBProperties.Tables("events", TABLE, HISTORY_TABLE, IDEMPOTENCY_TABLE)));
    }

    @Test
    void saveWritesTheOrderItsFirstHistoryEntryAndItsKeyInOneTransaction() {
        allowTransactions();

        StepVerifier.create(adapter.save(ORDER, RECORD))
                .expectNext(ORDER)
                .verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        assertThat(request.transactItems()).hasSize(3);

        Put order = request.transactItems().get(0).put();
        assertThat(order.tableName()).isEqualTo(TABLE);
        assertThat(order.conditionExpression()).isEqualTo("attribute_not_exists(#id)");
        assertThat(order.expressionAttributeNames()).containsEntry("#id", "id");
        assertThat(order.item())
                .containsEntry("id", AttributeValue.fromS("o-1"))
                .containsEntry("eventId", AttributeValue.fromS("e-1"))
                .containsEntry("customerId", AttributeValue.fromS("c-1"))
                .containsEntry("quantity", AttributeValue.fromN("2"))
                .containsEntry("status", AttributeValue.fromS("QUEUED"))
                .containsEntry("createdAt", AttributeValue.fromS("2026-10-10T12:00:00Z"))
                .containsEntry("updatedAt", AttributeValue.fromS("2026-10-10T12:00:00Z"))
                .doesNotContainKeys("expiresAt", "rejectionReason", "expiryShard");

        Put history = request.transactItems().get(1).put();
        assertThat(history.tableName()).isEqualTo(HISTORY_TABLE);
        assertThat(history.item())
                .containsEntry("orderId", AttributeValue.fromS("o-1"))
                .containsEntry("toStatus", AttributeValue.fromS("QUEUED"))
                .containsEntry("reason", AttributeValue.fromS("ORDER_PLACED"))
                .doesNotContainKey("fromStatus");

        Put key = request.transactItems().get(2).put();
        assertThat(key.tableName()).isEqualTo(IDEMPOTENCY_TABLE);
        assertThat(key.conditionExpression()).isEqualTo("attribute_not_exists(#key) OR #ttl < :now");
        assertThat(key.expressionAttributeValues())
                .containsEntry(":now", AttributeValue.fromN(String.valueOf(CREATED_AT.getEpochSecond())));
        assertThat(key.item())
                .containsEntry("idempotencyKey", AttributeValue.fromS("c-1#key-0001"))
                .containsEntry("orderId", AttributeValue.fromS("o-1"))
                .containsEntry("ttl", AttributeValue.fromN(String.valueOf(CREATED_AT.plusSeconds(86400).getEpochSecond())));
    }

    @Test
    void saveFailsWithDuplicateRequestWhenAnotherRequestTookTheKeyFirst() {
        cancelWith("None", "None", "ConditionalCheckFailed");

        StepVerifier.create(adapter.save(ORDER, RECORD))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DuplicateRequestException.class)
                        .hasMessage("another request with the key c-1#key-0001 was processed at the same time"))
                .verify();
    }

    @Test
    void saveFailsWithOrderAlreadyExistsWhenTheIdIsTaken() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.save(ORDER, RECORD))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderAlreadyExistsException.class)
                        .hasMessage("an order with id o-1 already exists"))
                .verify();
    }

    @Test
    void savePropagatesDynamoErrors() {
        when(lowLevelClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dynamo down")));

        StepVerifier.create(adapter.save(ORDER, RECORD))
                .expectErrorMessage("dynamo down")
                .verify();
    }

    @Test
    void saveKeepsOtherCancellations() {
        cancelWith("None", "ConditionalCheckFailed");

        StepVerifier.create(adapter.save(ORDER, RECORD))
                .expectError(TransactionCanceledException.class)
                .verify();
    }

    @Test
    void saveDoesNotWriteUntilSubscribed() {
        Mono<QueuedOrder> pending = adapter.save(ORDER, RECORD);

        verifyNoInteractions(lowLevelClient);
        assertThat(pending).isNotNull();
    }

    @Test
    void findByIdRebuildsAQueuedOrder() {
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(OrderEntity.from(ORDER)));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER)
                .verifyComplete();
    }

    @Test
    void findByIdReadsTheOrderWithAStronglyConsistentRead() {
        when(table.getItem(anyGetItemRequest()))
                .thenReturn(CompletableFuture.completedFuture(OrderEntity.from(ORDER)));

        StepVerifier.create(adapter.findById("o-1")).expectNext(ORDER).verifyComplete();

        GetItemEnhancedRequest request = capturedGetItemRequest();
        assertThat(request.consistentRead()).isTrue();
        assertThat(request.key().partitionKeyValue()).isEqualTo(AttributeValue.fromS("o-1"));
    }

    @Test
    void findByIdRebuildsAReservedOrder() {
        Instant expiresAt = CREATED_AT.plus(Duration.ofMinutes(10));
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("RESERVED");
        entity.setExpiresAt(expiresAt);
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reserve(CREATED_AT, Duration.ofMinutes(10)))
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsARejectedOrder() {
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("REJECTED");
        entity.setRejectionReason("SOLD_OUT");
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reject(RejectionReason.SOLD_OUT, CREATED_AT))
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsAnExpiredOrder() {
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("EXPIRED");
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reserve(CREATED_AT, Duration.ofMinutes(10)).expire(CREATED_AT))
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsASoldOrder() {
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("SOLD");
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reserve(CREATED_AT, Duration.ofMinutes(10)).confirm(CREATED_AT))
                .verifyComplete();
    }

    @Test
    void findExpiredReservationsReadsEveryShardAndKeepsOnlyReservedOrders() {
        var reserved = ORDER.reserve(CREATED_AT, Duration.ofMinutes(10));
        OrderEntity reservedEntity = OrderEntity.from(ORDER);
        reservedEntity.setStatus("RESERVED");
        reservedEntity.setExpiresAt(reserved.expiresAt());
        // The index is updated asynchronously, so it may still return an order that just left RESERVED
        OrderEntity staleEntity = OrderEntity.from(ORDER);
        staleEntity.setStatus("EXPIRED");
        when(expiringOrders.query(any(QueryConditional.class)))
                .thenReturn(pages(reservedEntity, staleEntity), pages(), pages(), pages());

        StepVerifier.create(adapter.findExpiredReservations(CREATED_AT.plus(Duration.ofMinutes(15))))
                .expectNext(reserved)
                .verifyComplete();

        verify(expiringOrders, times(ExpiringOrdersIndex.SHARDS)).query(any(QueryConditional.class));
    }

    @Test
    void findByIdIsEmptyWhenTheOrderDoesNotExist() {
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findById("missing")).verifyComplete();
    }

    @Test
    void markRejectedUpdatesTheOrderAndRecordsTheRejectionInOneTransaction() {
        allowTransactions();
        var rejected = ORDER.reject(RejectionReason.SOLD_OUT, CREATED_AT.plusSeconds(1));

        StepVerifier.create(adapter.markRejected(rejected))
                .expectNext(rejected)
                .verifyComplete();

        TransactWriteItemsRequest request = capturedTransaction();
        Update order = request.transactItems().get(0).update();
        assertThat(order.tableName()).isEqualTo(TABLE);
        assertThat(order.key()).containsEntry("id", AttributeValue.fromS("o-1"));
        assertThat(order.updateExpression())
                .isEqualTo("SET #status = :rejected, #rejectionReason = :rejectionReason, #updatedAt = :updatedAt");
        assertThat(order.conditionExpression()).isEqualTo("#status = :queued");
        assertThat(order.expressionAttributeValues())
                .containsEntry(":rejected", AttributeValue.fromS("REJECTED"))
                .containsEntry(":queued", AttributeValue.fromS("QUEUED"))
                .containsEntry(":rejectionReason", AttributeValue.fromS("SOLD_OUT"))
                .containsEntry(":updatedAt", AttributeValue.fromS("2026-10-10T12:00:01Z"));

        Put history = request.transactItems().get(1).put();
        assertThat(history.tableName()).isEqualTo(HISTORY_TABLE);
        assertThat(history.item())
                .containsEntry("fromStatus", AttributeValue.fromS("QUEUED"))
                .containsEntry("toStatus", AttributeValue.fromS("REJECTED"))
                .containsEntry("reason", AttributeValue.fromS("SOLD_OUT"));
    }

    @Test
    void markRejectedFailsWithOrderAlreadyProcessedWhenTheOrderIsNoLongerQueued() {
        cancelWith("ConditionalCheckFailed", "None");

        StepVerifier.create(adapter.markRejected(ORDER.reject(RejectionReason.SOLD_OUT, CREATED_AT)))
                .expectError(OrderAlreadyProcessedException.class)
                .verify();
    }

    private void allowTransactions() {
        when(lowLevelClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(TransactWriteItemsResponse.builder().build()));
    }

    private void cancelWith(String... codes) {
        TransactionCanceledException error = TransactionCanceledException.builder()
                .message("Transaction cancelled")
                .cancellationReasons(Arrays.stream(codes)
                        .map(code -> CancellationReason.builder().code(code).build())
                        .toList())
                .build();
        when(lowLevelClient.transactWriteItems(any(TransactWriteItemsRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(error));
    }

    private TransactWriteItemsRequest capturedTransaction() {
        ArgumentCaptor<TransactWriteItemsRequest> captor = ArgumentCaptor.forClass(TransactWriteItemsRequest.class);
        verify(lowLevelClient).transactWriteItems(captor.capture());
        return captor.getValue();
    }

    private static Consumer<GetItemEnhancedRequest.Builder> anyGetItemRequest() {
        return any();
    }

    // Applies the request consumer that the adapter passed to getItem, to inspect the resulting request
    @SuppressWarnings("unchecked")
    private GetItemEnhancedRequest capturedGetItemRequest() {
        ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest.Builder builder = GetItemEnhancedRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    private static SdkPublisher<Page<OrderEntity>> pages(OrderEntity... items) {
        return SdkPublisher.adapt(Flux.just(Page.create(List.of(items))));
    }

}

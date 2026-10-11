package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.exception.OrderAlreadyExistsException;
import co.com.ticketing.model.exception.OrderAlreadyProcessedException;
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
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderDynamoDBAdapterTest {

    private static final String TABLE = "local-ticketing-orders";
    private static final Instant CREATED_AT = Instant.parse("2026-10-10T12:00:00Z");
    private static final QueuedOrder ORDER = new QueuedOrder(new OrderDetails("o-1", "e-1", "c-1", 2, CREATED_AT));

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
                new DynamoDBProperties("us-east-1", null, new DynamoDBProperties.Tables("events", TABLE)));
    }

    @Test
    void savePersistsEveryFieldAndReturnsTheOrder() {
        when(table.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(ORDER))
                .expectNext(ORDER)
                .verifyComplete();

        OrderEntity saved = capturedRequest().item();
        assertThat(saved.getId()).isEqualTo("o-1");
        assertThat(saved.getEventId()).isEqualTo("e-1");
        assertThat(saved.getCustomerId()).isEqualTo("c-1");
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getStatus()).isEqualTo("QUEUED");
        assertThat(saved.getExpiresAt()).isNull();
        assertThat(saved.getRejectionReason()).isNull();
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void saveOnlyWritesWhenTheIdDoesNotExist() {
        when(table.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(ORDER)).expectNext(ORDER).verifyComplete();

        Expression condition = capturedRequest().conditionExpression();
        assertThat(condition.expression()).isEqualTo("attribute_not_exists(#id)");
        assertThat(condition.expressionNames()).containsEntry("#id", "id");
    }

    @Test
    void saveFailsWithOrderAlreadyExistsWhenTheIdIsTaken() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(conditionalCheckFailed())));

        StepVerifier.create(adapter.save(ORDER))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(OrderAlreadyExistsException.class)
                        .hasMessage("an order with id o-1 already exists"))
                .verify();
    }

    @Test
    void savePropagatesDynamoErrors() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dynamo down")));

        StepVerifier.create(adapter.save(ORDER))
                .expectErrorMessage("dynamo down")
                .verify();
    }

    @Test
    void saveDoesNotWriteUntilSubscribed() {
        Mono<QueuedOrder> pending = adapter.save(ORDER);

        verify(table, never()).putItem(any(PutItemEnhancedRequest.class));
        assertThat(pending).isNotNull();
    }

    @Test
    void findByIdRebuildsAQueuedOrder() {
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(OrderEntity.from(ORDER)));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER)
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsAReservedOrder() {
        Instant expiresAt = CREATED_AT.plus(Duration.ofMinutes(10));
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("RESERVED");
        entity.setExpiresAt(expiresAt);
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reserve(CREATED_AT, Duration.ofMinutes(10)))
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsARejectedOrder() {
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("REJECTED");
        entity.setRejectionReason("SOLD_OUT");
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reject(RejectionReason.SOLD_OUT))
                .verifyComplete();
    }

    @Test
    void findByIdRebuildsAnExpiredOrder() {
        OrderEntity entity = OrderEntity.from(ORDER);
        entity.setStatus("EXPIRED");
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(entity));

        StepVerifier.create(adapter.findById("o-1"))
                .expectNext(ORDER.reserve(CREATED_AT, Duration.ofMinutes(10)).expire())
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
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findById("missing")).verifyComplete();
    }

    @Test
    void markRejectedOnlyUpdatesTheStatusAndReasonOfAQueuedOrder() {
        when(lowLevelClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(UpdateItemResponse.builder().build()));
        var rejected = ORDER.reject(RejectionReason.SOLD_OUT);

        StepVerifier.create(adapter.markRejected(rejected))
                .expectNext(rejected)
                .verifyComplete();

        ArgumentCaptor<UpdateItemRequest> captor = ArgumentCaptor.forClass(UpdateItemRequest.class);
        verify(lowLevelClient).updateItem(captor.capture());
        UpdateItemRequest request = captor.getValue();
        assertThat(request.tableName()).isEqualTo(TABLE);
        assertThat(request.key()).containsEntry("id", AttributeValue.fromS("o-1"));
        assertThat(request.updateExpression()).isEqualTo("SET #status = :rejected, #rejectionReason = :rejectionReason");
        assertThat(request.conditionExpression()).isEqualTo("#status = :queued");
        assertThat(request.expressionAttributeValues())
                .containsEntry(":rejected", AttributeValue.fromS("REJECTED"))
                .containsEntry(":queued", AttributeValue.fromS("QUEUED"))
                .containsEntry(":rejectionReason", AttributeValue.fromS("SOLD_OUT"));
    }

    @Test
    void markRejectedFailsWithOrderAlreadyProcessedWhenTheOrderIsNoLongerQueued() {
        when(lowLevelClient.updateItem(any(UpdateItemRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(conditionalCheckFailed()));

        StepVerifier.create(adapter.markRejected(ORDER.reject(RejectionReason.SOLD_OUT)))
                .expectError(OrderAlreadyProcessedException.class)
                .verify();
    }

    @SuppressWarnings("unchecked")
    private PutItemEnhancedRequest<OrderEntity> capturedRequest() {
        ArgumentCaptor<PutItemEnhancedRequest<OrderEntity>> captor =
                ArgumentCaptor.forClass(PutItemEnhancedRequest.class);
        verify(table).putItem(captor.capture());
        return captor.getValue();
    }

    private static SdkPublisher<Page<OrderEntity>> pages(OrderEntity... items) {
        return SdkPublisher.adapt(Flux.just(Page.create(List.of(items))));
    }

    private static ConditionalCheckFailedException conditionalCheckFailed() {
        return ConditionalCheckFailedException.builder().message("The conditional request failed").build();
    }
}

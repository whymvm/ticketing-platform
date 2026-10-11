package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.order.IdempotencyKey;
import co.com.ticketing.model.order.IdempotencyRecord;
import co.com.ticketing.model.order.NewOrder;
import co.com.ticketing.model.order.QueuedOrder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.GetItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdempotencyDynamoDBAdapterTest {

    private static final String TABLE = "local-ticketing-idempotency";
    private static final Instant NOW = Instant.parse("2026-10-11T12:00:00Z");
    private static final IdempotencyRecord RECORD = IdempotencyRecord.of(new IdempotencyKey("c-1", "key-0001"),
            QueuedOrder.create("o-1", new NewOrder("e-1", 2), "c-1", NOW));

    @Mock
    private DynamoDbEnhancedAsyncClient client;

    @Mock
    private DynamoDbAsyncTable<IdempotencyEntity> table;

    private IdempotencyDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.table(eq(TABLE), any(TableSchema.class))).thenReturn(table);
        adapter = new IdempotencyDynamoDBAdapter(client, new DynamoDBProperties("us-east-1", null,
                new DynamoDBProperties.Tables("events", "orders", "order-history", TABLE)));
    }

    @Test
    void findsTheRecordOfTheKeyWithAStronglyConsistentRead() {
        when(table.getItem(anyGetItemRequest()))
                .thenReturn(CompletableFuture.completedFuture(IdempotencyEntity.from(RECORD)));

        StepVerifier.create(adapter.findByKey("c-1#key-0001"))
                .expectNext(RECORD)
                .verifyComplete();

        GetItemEnhancedRequest request = capturedRequest();
        assertThat(request.consistentRead()).isTrue();
        assertThat(request.key().partitionKeyValue()).isEqualTo(AttributeValue.fromS("c-1#key-0001"));
    }

    @Test
    void isEmptyWhenTheKeyWasNeverUsed() {
        when(table.getItem(anyGetItemRequest())).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findByKey("c-1#unknown-key")).verifyComplete();
    }

    @Test
    void theItemExpiresTwentyFourHoursAfterTheRequest() {
        IdempotencyEntity entity = IdempotencyEntity.from(RECORD);

        assertThat(entity.getTtl()).isEqualTo(NOW.plusSeconds(86400).getEpochSecond());
        assertThat(entity.toDomain()).isEqualTo(RECORD);
    }

    private static Consumer<GetItemEnhancedRequest.Builder> anyGetItemRequest() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private GetItemEnhancedRequest capturedRequest() {
        ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(table).getItem(captor.capture());
        GetItemEnhancedRequest.Builder builder = GetItemEnhancedRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }
}

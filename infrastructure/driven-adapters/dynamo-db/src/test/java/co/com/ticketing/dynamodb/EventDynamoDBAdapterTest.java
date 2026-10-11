package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
import co.com.ticketing.model.exception.EventAlreadyExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventDynamoDBAdapterTest {

    private static final String TABLE = "local-ticketing-events";
    private static final Instant CREATED_AT = Instant.parse("2026-10-09T12:00:00Z");
    private static final Event EVENT = new Event("e-1", "Concert", "Arena",
            Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000,
            new Inventory(4000, 600, 200, 150, 50), CREATED_AT);

    @Mock
    private DynamoDbEnhancedAsyncClient client;

    @Mock
    private DynamoDbAsyncTable<EventEntity> table;

    private EventDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.table(eq(TABLE), any(TableSchema.class))).thenReturn(table);
        adapter = new EventDynamoDBAdapter(client,
                new DynamoDBProperties("us-east-1", null, new DynamoDBProperties.Tables(TABLE, "local-ticketing-orders", "local-ticketing-order-history", "local-ticketing-idempotency")));
    }

    @Test
    void savePersistsEveryFieldAndReturnsTheEvent() {
        when(table.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(EVENT))
                .expectNext(EVENT)
                .verifyComplete();

        EventEntity saved = capturedRequest().item();
        assertThat(saved.getId()).isEqualTo("e-1");
        assertThat(saved.getName()).isEqualTo("Concert");
        assertThat(saved.getVenue()).isEqualTo("Arena");
        assertThat(saved.getStartsAt()).isEqualTo(EVENT.startsAt());
        assertThat(saved.getFinishesAt()).isEqualTo(EVENT.finishesAt());
        assertThat(saved.getCapacity()).isEqualTo(5000);
        assertThat(saved.getAvailable()).isEqualTo(4000);
        assertThat(saved.getReserved()).isEqualTo(600);
        assertThat(saved.getPendingConfirmation()).isEqualTo(200);
        assertThat(saved.getSold()).isEqualTo(150);
        assertThat(saved.getComplimentary()).isEqualTo(50);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void saveOnlyWritesWhenTheIdDoesNotExist() {
        when(table.putItem(any(PutItemEnhancedRequest.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(EVENT)).expectNext(EVENT).verifyComplete();

        Expression condition = capturedRequest().conditionExpression();
        assertThat(condition.expression()).isEqualTo("attribute_not_exists(#id)");
        assertThat(condition.expressionNames()).containsEntry("#id", "id");
    }

    @Test
    void saveFailsWithEventAlreadyExistsWhenTheIdIsTaken() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(conditionalCheckFailed()));

        StepVerifier.create(adapter.save(EVENT))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(EventAlreadyExistsException.class)
                        .hasMessage("an event with id e-1 already exists"))
                .verify();
    }

    @Test
    void saveDetectsTheTakenIdWhenTheSdkWrapsTheError() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(conditionalCheckFailed())));

        StepVerifier.create(adapter.save(EVENT))
                .expectError(EventAlreadyExistsException.class)
                .verify();
    }

    @Test
    void savePropagatesDynamoErrors() {
        when(table.putItem(any(PutItemEnhancedRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dynamo down")));

        StepVerifier.create(adapter.save(EVENT))
                .expectErrorMessage("dynamo down")
                .verify();
    }

    @Test
    void saveDoesNotWriteUntilSubscribed() {
        Mono<Event> pending = adapter.save(EVENT);

        verifyNoInteractions(table);
        assertThat(pending).isNotNull();
    }

    @Test
    void findByIdRebuildsTheStoredEvent() {
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(EventEntity.from(EVENT)));

        StepVerifier.create(adapter.findById("e-1"))
                .expectNext(EVENT)
                .verifyComplete();

        ArgumentCaptor<Key> key = ArgumentCaptor.forClass(Key.class);
        verify(table).getItem(key.capture());
        assertThat(key.getValue().partitionKeyValue().s()).isEqualTo("e-1");
    }

    @Test
    void findByIdIsEmptyWhenNoEventHasTheId() {
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.findById("missing"))
                .verifyComplete();
    }

    @Test
    void findByIdPropagatesDynamoErrors() {
        when(table.getItem(any(Key.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("dynamo down")));

        StepVerifier.create(adapter.findById("e-1"))
                .expectErrorMessage("dynamo down")
                .verify();
    }

    @Test
    void findByIdDoesNotReadUntilSubscribed() {
        Mono<Event> pending = adapter.findById("e-1");

        verifyNoInteractions(table);
        assertThat(pending).isNotNull();
    }

    @SuppressWarnings("unchecked")
    private PutItemEnhancedRequest<EventEntity> capturedRequest() {
        ArgumentCaptor<PutItemEnhancedRequest<EventEntity>> captor =
                ArgumentCaptor.forClass(PutItemEnhancedRequest.class);
        verify(table).putItem(captor.capture());
        return captor.getValue();
    }

    private static ConditionalCheckFailedException conditionalCheckFailed() {
        return ConditionalCheckFailedException.builder().message("The conditional request failed").build();
    }
}

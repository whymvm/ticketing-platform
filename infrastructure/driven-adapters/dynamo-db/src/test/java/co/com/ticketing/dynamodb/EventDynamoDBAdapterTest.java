package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.Inventory;
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
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

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
                new DynamoDBProperties("us-east-1", null, new DynamoDBProperties.Tables(TABLE)));
    }

    @Test
    void savePersistsEveryFieldAndReturnsTheEvent() {
        when(table.putItem(any(EventEntity.class))).thenReturn(CompletableFuture.completedFuture(null));

        StepVerifier.create(adapter.save(EVENT))
                .expectNext(EVENT)
                .verifyComplete();

        ArgumentCaptor<EventEntity> captor = ArgumentCaptor.forClass(EventEntity.class);
        verify(table).putItem(captor.capture());
        EventEntity saved = captor.getValue();
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
    void savePropagatesDynamoErrors() {
        when(table.putItem(any(EventEntity.class)))
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
}

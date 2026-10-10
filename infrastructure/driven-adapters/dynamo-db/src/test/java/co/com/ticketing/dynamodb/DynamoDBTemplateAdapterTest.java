package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.reactivecommons.utils.ObjectMapper;
import reactor.test.StepVerifier;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DynamoDBTemplateAdapterTest {

    private static final Instant CREATED_AT = Instant.parse("2026-10-09T12:00:00Z");
    private static final Event EVENT = new Event("e-1", "Concert", "Arena",
            Instant.parse("2026-12-05T01:00:00Z"), Instant.parse("2026-12-05T04:00:00Z"), 5000, CREATED_AT);

    @Mock
    private DynamoDbEnhancedAsyncClient client;

    @Mock
    private DynamoDbAsyncTable<EventEntity> table;

    @Mock
    private ObjectMapper mapper;

    private DynamoDBTemplateAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.table(eq("local-ticketing-events"), any(TableSchema.class))).thenReturn(table);
        adapter = new DynamoDBTemplateAdapter(client, mapper, new DynamoDBProperties("us-east-1", null,
                new DynamoDBProperties.Tables("local-ticketing-events")));
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
    void getByIdMapsStoredEntityBackToTheDomain() {
        EventEntity stored = new EventEntity("e-1", "Concert", "Arena", EVENT.startsAt(), EVENT.finishesAt(),
                5000, CREATED_AT);
        when(table.getItem(any(Key.class))).thenReturn(CompletableFuture.completedFuture(stored));

        StepVerifier.create(adapter.getById("e-1"))
                .expectNext(EVENT)
                .verifyComplete();
    }
}

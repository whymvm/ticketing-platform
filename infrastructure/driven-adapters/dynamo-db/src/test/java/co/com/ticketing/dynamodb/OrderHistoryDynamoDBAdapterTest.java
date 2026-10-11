package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.TransitionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.async.SdkPublisher;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PagePublisher;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderHistoryDynamoDBAdapterTest {

    private static final String TABLE = "local-ticketing-order-history";
    private static final OrderHistoryEntry PLACED = new OrderHistoryEntry("o-1", null, OrderStatus.QUEUED,
            TransitionReason.ORDER_PLACED, Instant.parse("2026-10-11T12:00:00Z"));
    private static final OrderHistoryEntry RESERVED = new OrderHistoryEntry("o-1", OrderStatus.QUEUED,
            OrderStatus.RESERVED, TransitionReason.TICKETS_RESERVED, Instant.parse("2026-10-11T12:00:01Z"));

    @Mock
    private DynamoDbEnhancedAsyncClient client;

    @Mock
    private DynamoDbAsyncTable<OrderHistoryEntity> table;

    private OrderHistoryDynamoDBAdapter adapter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(client.table(eq(TABLE), any(TableSchema.class))).thenReturn(table);
        adapter = new OrderHistoryDynamoDBAdapter(client, new DynamoDBProperties("us-east-1", null,
                new DynamoDBProperties.Tables("events", "orders", TABLE)));
    }

    @Test
    void returnsTheEntriesOfTheOrderInTheOrderTheQueryReturnsThem() {
        when(table.query(anyQuery())).thenReturn(pages(OrderHistoryEntity.from(PLACED), OrderHistoryEntity.from(RESERVED)));

        StepVerifier.create(adapter.findByOrderId("o-1"))
                .expectNext(PLACED, RESERVED)
                .verifyComplete();
    }

    @Test
    void queriesTheOrderWithAStronglyConsistentRead() {
        when(table.query(anyQuery())).thenReturn(pages());

        StepVerifier.create(adapter.findByOrderId("o-1")).verifyComplete();

        QueryEnhancedRequest request = capturedQuery();
        assertThat(request.consistentRead()).isTrue();
        assertThat(request.queryConditional()).isNotNull();
    }

    private static Consumer<QueryEnhancedRequest.Builder> anyQuery() {
        return any();
    }

    @SuppressWarnings("unchecked")
    private QueryEnhancedRequest capturedQuery() {
        ArgumentCaptor<Consumer<QueryEnhancedRequest.Builder>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(table).query(captor.capture());
        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder();
        captor.getValue().accept(builder);
        return builder.build();
    }

    private static PagePublisher<OrderHistoryEntity> pages(OrderHistoryEntity... items) {
        return PagePublisher.create(SdkPublisher.adapt(Flux.just(Page.create(List.of(items)))));
    }
}

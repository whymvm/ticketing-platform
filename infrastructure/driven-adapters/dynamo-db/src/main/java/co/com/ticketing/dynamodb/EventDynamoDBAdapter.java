package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventCreator;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/**
 * Stores events in DynamoDB.
 */
@Repository
public class EventDynamoDBAdapter implements EventCreator {

    private final DynamoDbAsyncTable<EventEntity> table;

    public EventDynamoDBAdapter(DynamoDbEnhancedAsyncClient client, DynamoDBProperties properties) {
        this.table = client.table(properties.tables().events(), TableSchema.fromBean(EventEntity.class));
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromFuture(() -> table.putItem(EventEntity.from(event)))
                .thenReturn(event);
    }
}

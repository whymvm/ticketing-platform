package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventCreator;
import co.com.ticketing.model.event.gateways.EventRetriever;
import co.com.ticketing.model.exception.EventAlreadyExistsException;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

/**
 * Stores and retrieves events in DynamoDB.
 */
@Repository
public class EventDynamoDBAdapter implements EventCreator, EventRetriever {

    private final DynamoDbAsyncTable<EventEntity> table;

    public EventDynamoDBAdapter(DynamoDbEnhancedAsyncClient client, DynamoDBProperties properties) {
        this.table = client.table(properties.tables().events(), TableSchema.fromBean(EventEntity.class));
    }

    @Override
    public Mono<Event> save(Event event) {
        return Mono.fromFuture(() -> table.putItem(newItemRequest(event)))
                .onErrorMap(ConditionalCheckFailedException.class, conditionalCheckFailedException -> new EventAlreadyExistsException(event.id()))
                .thenReturn(event);
    }

    @Override
    public Mono<Event> findById(String id) {
        return Mono.fromFuture(() -> table.getItem(Key.builder().partitionValue(id).build()))
                .map(EventEntity::toDomain);
    }

    private static PutItemEnhancedRequest<EventEntity> newItemRequest(Event event) {
        return PutItemEnhancedRequest.builder(EventEntity.class)
                .item(EventEntity.from(event))
                .conditionExpression(DynamoDBExpressions.ID_NOT_EXISTS)
                .build();
    }
}

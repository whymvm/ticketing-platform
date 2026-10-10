package co.com.ticketing.dynamodb;

import co.com.ticketing.dynamodb.config.DynamoDBProperties;
import co.com.ticketing.dynamodb.helper.TemplateAdapterOperations;
import co.com.ticketing.model.event.Event;
import co.com.ticketing.model.event.gateways.EventCreator;
import org.reactivecommons.utils.ObjectMapper;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;


/**
 * Stores events in DynamoDB. The save method is inherited from {@link TemplateAdapterOperations}.
 */
@Repository
public class DynamoDBTemplateAdapter extends TemplateAdapterOperations<Event, String, EventEntity>
        implements EventCreator {

    public DynamoDBTemplateAdapter(DynamoDbEnhancedAsyncClient connectionFactory,
                                   ObjectMapper mapper,
                                   DynamoDBProperties properties) {
        super(connectionFactory, mapper, DynamoDBTemplateAdapter::toDomain, properties.tables().events());
    }

    @Override
    protected EventEntity toEntity(Event event) {
        return new EventEntity(event.id(), event.name(), event.venue(), event.startsAt(),
                event.finishesAt(), event.capacity(), event.createdAt());
    }

    private static Event toDomain(EventEntity entity) {
        return new Event(entity.getId(), entity.getName(), entity.getVenue(), entity.getStartsAt(),
                entity.getFinishesAt(), entity.getCapacity(), entity.getCreatedAt());
    }
}

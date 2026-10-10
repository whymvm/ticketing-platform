package co.com.ticketing.dynamodb;

import co.com.ticketing.model.event.Event;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Event item in DynamoDB. Inventory counters are top-level attributes so they can be updated atomically.
 */
@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class EventEntity {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String id;
    private String name;
    private String venue;
    private Instant startsAt;
    private Instant finishesAt;
    private Integer capacity;
    private Integer available;
    private Integer reserved;
    private Integer pendingConfirmation;
    private Integer sold;
    private Integer complimentary;
    private Instant createdAt;

    /**
     * Builds the item from an event.
     */
    public static EventEntity from(Event event) {
        EventEntity entity = new EventEntity();
        entity.setId(event.id());
        entity.setName(event.name());
        entity.setVenue(event.venue());
        entity.setStartsAt(event.startsAt());
        entity.setFinishesAt(event.finishesAt());
        entity.setCapacity(event.capacity());
        entity.setAvailable(event.inventory().available());
        entity.setReserved(event.inventory().reserved());
        entity.setPendingConfirmation(event.inventory().pendingConfirmation());
        entity.setSold(event.inventory().sold());
        entity.setComplimentary(event.inventory().complimentary());
        entity.setCreatedAt(event.createdAt());
        return entity;
    }
}

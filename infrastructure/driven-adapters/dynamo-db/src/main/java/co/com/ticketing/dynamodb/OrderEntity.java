package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.QueuedOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Order item in DynamoDB.
 */
@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class OrderEntity {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String id;
    private String eventId;
    private String customerId;
    private Integer quantity;
    private String status;
    private Instant createdAt;

    /**
     * Builds the item from a new order.
     */
    public static OrderEntity from(QueuedOrder order) {
        OrderDetails details = order.details();
        OrderEntity entity = new OrderEntity();
        entity.setId(details.id());
        entity.setEventId(details.eventId());
        entity.setCustomerId(details.customerId());
        entity.setQuantity(details.quantity());
        entity.setStatus(order.status().name());
        entity.setCreatedAt(details.createdAt());
        return entity;
    }
}

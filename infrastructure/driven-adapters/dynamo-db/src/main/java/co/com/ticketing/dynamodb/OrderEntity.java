package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.Order;
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
     * Builds the item from an order.
     */
    public static OrderEntity from(Order order) {
        OrderEntity entity = new OrderEntity();
        entity.setId(order.id());
        entity.setEventId(order.eventId());
        entity.setCustomerId(order.customerId());
        entity.setQuantity(order.quantity());
        entity.setStatus(order.status().name());
        entity.setCreatedAt(order.createdAt());
        return entity;
    }
}

package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.ExpiredOrder;
import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderDetails;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.QueuedOrder;
import co.com.ticketing.model.order.RejectedOrder;
import co.com.ticketing.model.order.RejectionReason;
import co.com.ticketing.model.order.ReservedOrder;
import co.com.ticketing.model.order.SoldOrder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

import java.time.Instant;
import java.util.Optional;

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
    @Getter(onMethod_ = @DynamoDbSecondarySortKey(indexNames = ExpiringOrdersIndex.NAME))
    private Instant expiresAt;
    @Getter(onMethod_ = @DynamoDbSecondaryPartitionKey(indexNames = ExpiringOrdersIndex.NAME))
    private String expiryShard;
    private String rejectionReason;
    private Instant createdAt;
    private Instant updatedAt;

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
        entity.setUpdatedAt(order.updatedAt());
        return entity;
    }

    /**
     * Rebuilds the order in the state stored in the item.
     */
    public Order toDomain() {
        OrderDetails details = new OrderDetails(id, eventId, customerId, quantity, createdAt);
        // Orders saved before updatedAt existed fall back to their creation time
        Instant changedAt = Optional.ofNullable(updatedAt).orElse(createdAt);
        return switch (OrderStatus.valueOf(status)) {
            case QUEUED -> new QueuedOrder(details);
            case RESERVED -> new ReservedOrder(details, expiresAt, changedAt);
            case REJECTED -> new RejectedOrder(details, RejectionReason.valueOf(rejectionReason), changedAt);
            case EXPIRED -> new ExpiredOrder(details, changedAt);
            case SOLD -> new SoldOrder(details, changedAt);
        };
    }
}

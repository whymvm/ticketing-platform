package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.OrderHistoryEntry;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.model.order.TransitionReason;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Order history item in DynamoDB. The sort key starts with the instant in a fixed-width format, so the entries of
 * an order come back in time order, and ends with the new state, which an order reaches only once.
 */
@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class OrderHistoryEntity {

    private static final DateTimeFormatter SORTABLE_INSTANT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSSSSS'Z'").withZone(ZoneOffset.UTC);

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String orderId;
    @Getter(onMethod_ = @DynamoDbSortKey)
    private String sk;
    private String fromStatus;
    private String toStatus;
    private String reason;
    private Instant occurredAt;

    /**
     * Builds the item from a history entry.
     */
    public static OrderHistoryEntity from(OrderHistoryEntry entry) {
        OrderHistoryEntity entity = new OrderHistoryEntity();
        entity.setOrderId(entry.orderId());
        entity.setSk(SORTABLE_INSTANT.format(entry.occurredAt()) + "#" + entry.toStatus().name());
        entity.setFromStatus(Optional.ofNullable(entry.fromStatus()).map(OrderStatus::name).orElse(null));
        entity.setToStatus(entry.toStatus().name());
        entity.setReason(entry.reason().name());
        entity.setOccurredAt(entry.occurredAt());
        return entity;
    }

    /**
     * Rebuilds the history entry stored in the item.
     */
    public OrderHistoryEntry toDomain() {
        return new OrderHistoryEntry(orderId,
                Optional.ofNullable(fromStatus).map(OrderStatus::valueOf).orElse(null),
                OrderStatus.valueOf(toStatus), TransitionReason.valueOf(reason), occurredAt);
    }
}

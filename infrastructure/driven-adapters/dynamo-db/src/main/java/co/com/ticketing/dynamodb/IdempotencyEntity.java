package co.com.ticketing.dynamodb;

import co.com.ticketing.model.order.IdempotencyRecord;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

/**
 * Idempotency record in DynamoDB. The ttl attribute (epoch seconds) lets DynamoDB delete the record once it
 * expires; deleting it late does not matter, because the application ignores expired records.
 */
@DynamoDbBean
@Getter
@Setter
@NoArgsConstructor
public class IdempotencyEntity {

    @Getter(onMethod_ = @DynamoDbPartitionKey)
    private String idempotencyKey;
    private String requestFingerprint;
    private String orderId;
    private Instant createdAt;
    private Long ttl;

    /**
     * Builds the item from an idempotency record.
     */
    public static IdempotencyEntity from(IdempotencyRecord record) {
        IdempotencyEntity entity = new IdempotencyEntity();
        entity.setIdempotencyKey(record.key());
        entity.setRequestFingerprint(record.requestFingerprint());
        entity.setOrderId(record.orderId());
        entity.setCreatedAt(record.createdAt());
        entity.setTtl(record.expiresAt().getEpochSecond());
        return entity;
    }

    /**
     * Rebuilds the idempotency record stored in the item.
     */
    public IdempotencyRecord toDomain() {
        return new IdempotencyRecord(idempotencyKey, requestFingerprint, orderId, createdAt);
    }
}

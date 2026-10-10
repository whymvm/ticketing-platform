package co.com.ticketing.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

import java.time.Instant;

@DynamoDbBean
public class EventEntity {

    private String id;
    private String name;
    private String venue;
    private Instant startsAt;
    private Instant finishesAt;
    private Integer capacity;
    private Instant createdAt;

    public EventEntity() {
    }

    public EventEntity(String id, String name, String venue, Instant startsAt, Instant finishesAt,
                       Integer capacity, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.venue = venue;
        this.startsAt = startsAt;
        this.finishesAt = finishesAt;
        this.capacity = capacity;
        this.createdAt = createdAt;
    }

    @DynamoDbPartitionKey
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVenue() {
        return venue;
    }

    public void setVenue(String venue) {
        this.venue = venue;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getFinishesAt() {
        return finishesAt;
    }

    public void setFinishesAt(Instant finishesAt) {
        this.finishesAt = finishesAt;
    }

    public Integer getCapacity() {
        return capacity;
    }

    public void setCapacity(Integer capacity) {
        this.capacity = capacity;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}

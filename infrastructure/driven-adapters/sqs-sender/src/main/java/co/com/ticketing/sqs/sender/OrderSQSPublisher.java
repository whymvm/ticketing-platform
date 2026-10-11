package co.com.ticketing.sqs.sender;

import co.com.ticketing.model.order.OrderPlaced;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import co.com.ticketing.sqs.sender.config.SQSSenderConfig;
import co.com.ticketing.sqs.sender.config.SQSSenderProperties;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes order events to the SQS reservations queue.
 */
@Component
public class OrderSQSPublisher implements OrderPublisher {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SqsAsyncClient client;
    private final String queueUrl;

    public OrderSQSPublisher(@Qualifier(SQSSenderConfig.CLIENT) SqsAsyncClient client, SQSSenderProperties properties) {
        this.client = client;
        this.queueUrl = properties.queues().reservations();
    }

    @Override
    public Mono<Void> publish(OrderPlaced event) {
        return Mono.fromCallable(() -> request(event))
                .flatMap(request -> Mono.fromFuture(() -> client.sendMessage(request)))
                .then();
    }

    private SendMessageRequest request(OrderPlaced event) {
        return SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(JSON.writeValueAsString(OrderPlacedMessage.from(event)))
                .build();
    }
}

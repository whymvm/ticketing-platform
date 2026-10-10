package co.com.ticketing.sqs.sender;

import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.gateways.OrderPublisher;
import co.com.ticketing.sqs.sender.config.SQSSenderProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Queues orders in the SQS reservations queue.
 */
@Component
public class OrderSQSPublisher implements OrderPublisher {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final SqsAsyncClient client;
    private final String queueUrl;

    public OrderSQSPublisher(SqsAsyncClient client, SQSSenderProperties properties) {
        this.client = client;
        this.queueUrl = properties.queues().reservations();
    }

    @Override
    public Mono<Void> publish(Order order) {
        return Mono.fromCallable(() -> request(order))
                .flatMap(request -> Mono.fromFuture(() -> client.sendMessage(request)))
                .then();
    }

    private SendMessageRequest request(Order order) {
        return SendMessageRequest.builder()
                .queueUrl(queueUrl)
                .messageBody(JSON.writeValueAsString(ReserveTicketsMessage.of(order.id())))
                .build();
    }
}

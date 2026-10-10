package co.com.ticketing.sqs.sender;

import co.com.ticketing.model.order.Order;
import co.com.ticketing.model.order.OrderStatus;
import co.com.ticketing.sqs.sender.config.SQSSenderProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderSQSPublisherTest {

    private static final String QUEUE_URL = "http://localstack:4566/000000000000/local-ticketing-reservations";
    private static final Order ORDER = new Order("o-1", "e-1", "c-1", 2, OrderStatus.QUEUED,
            Instant.parse("2026-10-10T12:00:00Z"));

    @Mock
    private SqsAsyncClient client;

    private OrderSQSPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new OrderSQSPublisher(client, new SQSSenderProperties("us-east-1", null,
                new SQSSenderProperties.Queues(QUEUE_URL)));
    }

    @Test
    void sendsOnlyTheOrderIdToTheReservationsQueue() {
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(SendMessageResponse.builder().messageId("m-1").build()));

        StepVerifier.create(publisher.publish(ORDER)).verifyComplete();

        ArgumentCaptor<SendMessageRequest> request = ArgumentCaptor.forClass(SendMessageRequest.class);
        verify(client).sendMessage(request.capture());
        assertThat(request.getValue().queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(request.getValue().messageBody()).isEqualTo("{\"type\":\"RESERVE_TICKETS\",\"orderId\":\"o-1\"}");
    }

    @Test
    void propagatesSqsErrors() {
        when(client.sendMessage(any(SendMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("queue down")));

        StepVerifier.create(publisher.publish(ORDER))
                .expectErrorMessage("queue down")
                .verify();
    }

    @Test
    void doesNotSendUntilSubscribed() {
        Mono<Void> pending = publisher.publish(ORDER);

        verifyNoInteractions(client);
        assertThat(pending).isNotNull();
    }
}

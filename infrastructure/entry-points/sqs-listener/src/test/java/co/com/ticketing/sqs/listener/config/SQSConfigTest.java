package co.com.ticketing.sqs.listener.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.metrics.LoggingMetricPublisher;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSConfigTest {

    private final SQSConfig config = new SQSConfig();

    @Mock
    private SqsAsyncClient client;

    @Test
    void sqsListenerStartsPolling() {
        // A poll that never completes keeps the listener waiting, so the test does not loop
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(new CompletableFuture<>());

        assertThat(config.sqsListener(client, properties(null), message -> Mono.empty(),
                new SimpleMeterRegistry())).isNotNull();
        verify(client, timeout(1000)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void sqsListenerClientWorksWithAndWithoutAnEndpoint() {
        var publisher = LoggingMetricPublisher.create();

        assertThat(config.sqsListenerClient(properties("http://localhost:4566"), publisher)).isNotNull();
        assertThat(config.sqsListenerClient(properties(" "), publisher)).isNotNull();
        assertThat(config.sqsListenerClient(properties(null), publisher)).isNotNull();
    }

    private static SQSProperties properties(String endpoint) {
        return new SQSProperties("us-east-1", endpoint, "http://localhost:4566/000000000000/queue", 20, 10, 1);
    }
}

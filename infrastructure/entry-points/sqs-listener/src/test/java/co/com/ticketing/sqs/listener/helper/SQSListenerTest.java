package co.com.ticketing.sqs.listener.helper;

import co.com.ticketing.sqs.listener.config.SQSProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSListenerTest {

    private static final SQSProperties PROPERTIES = new SQSProperties("us-east-1", "http://localhost:4566",
            "http://localhost:4566/000000000000/queue", 20, 10, 1);
    private static final Message MESSAGE = Message.builder().messageId("m-1").receiptHandle("r-1").body("{}").build();

    @Mock
    private SqsAsyncClient client;

    private final AtomicInteger processed = new AtomicInteger();
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        processed.set(0);
        meterRegistry = new SimpleMeterRegistry();
    }

    @Test
    void deletesTheMessageAfterProcessingIt() {
        receive(CompletableFuture.completedFuture(withMessage()));
        when(client.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        StepVerifier.create(listen(listener(counting(Mono.empty())))).verifyComplete();

        assertThat(processed).hasValue(1);
        verify(client).deleteMessage(DeleteMessageRequest.builder()
                .queueUrl(PROPERTIES.queueUrl()).receiptHandle("r-1").build());
    }

    @Test
    void recordsTheDurationOfEachMessageInTheMeterRegistry() {
        receive(CompletableFuture.completedFuture(withMessage()));
        when(client.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));

        StepVerifier.create(listen(listener(counting(Mono.empty())))).verifyComplete();

        assertThat(meterRegistry.find("async_operation.flow.duration").tag("operation", "operation").timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Test
    void keepsTheMessageWhenProcessingFails() {
        receive(CompletableFuture.completedFuture(withMessage()));

        StepVerifier.create(listen(listener(counting(Mono.error(new IllegalStateException("dynamo down"))))))
                .verifyComplete();

        assertThat(processed).hasValue(1);
        verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    void asksTheQueueWithLongPollingAndWithoutOverridingTheVisibilityTimeout() {
        receive(CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build()));

        StepVerifier.create(listen(listener(counting(Mono.empty())))).verifyComplete();

        verify(client).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(PROPERTIES.queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20)
                .build());
    }

    @Test
    void keepsListeningAfterAFailedPoll() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sqs down")))
                .thenReturn(CompletableFuture.completedFuture(withMessage()))
                .thenReturn(new CompletableFuture<>());
        when(client.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
        SQSListener listener = listener(counting(Mono.empty()));

        StepVerifier.withVirtualTime(() -> ReflectionTestUtils.<Flux<Void>>invokeMethod(listener, "listenRetryRepeat"))
                .thenAwait(Duration.ofSeconds(5))
                .thenCancel()
                .verify();

        assertThat(processed).hasValue(1);
    }

    private SQSListener listener(Function<Message, Mono<Void>> processor) {
        return SQSListener.builder()
                .client(client)
                .properties(PROPERTIES)
                .processor(processor)
                .meterRegistry(meterRegistry)
                .operation("operation")
                .build();
    }

    private Function<Message, Mono<Void>> counting(Mono<Void> result) {
        return message -> Mono.defer(() -> {
            processed.incrementAndGet();
            return result;
        });
    }

    private void receive(CompletableFuture<ReceiveMessageResponse> response) {
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(response);
    }

    private static ReceiveMessageResponse withMessage() {
        return ReceiveMessageResponse.builder().messages(MESSAGE).build();
    }

    private static Flux<Void> listen(SQSListener listener) {
        return ReflectionTestUtils.invokeMethod(listener, "listen");
    }
}

package co.com.ticketing.sqs.listener.helper;

import co.com.ticketing.sqs.listener.config.SQSProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSListenerTest {

    private static final long WAIT_MS = 3000;
    private static final SQSProperties PROPERTIES = new SQSProperties("us-east-1", "http://localhost:4566",
            "http://localhost:4566/000000000000/queue", 20, 10, 1);

    @Mock
    private SqsAsyncClient client;

    private final AtomicInteger processed = new AtomicInteger();
    private SimpleMeterRegistry meterRegistry;
    private SQSListener listener;

    @BeforeEach
    void setUp() {
        processed.set(0);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        if (listener != null) {
            listener.stop();
        }
    }

    @Test
    void deletesTheMessageAfterProcessingIt() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(withMessage("m-1"), pendingPoll());
        allowDeletes();

        start(counting(message -> Mono.empty()));

        verify(client, timeout(WAIT_MS)).deleteMessage(deleteRequest("m-1"));
        assertThat(processed).hasValue(1);
    }

    @Test
    void keepsAFailedMessageAndKeepsPolling() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(withMessage("m-1"), withMessage("m-2"), pendingPoll());
        allowDeletes();

        start(counting(message -> "m-1".equals(message.messageId())
                ? Mono.error(new IllegalStateException("dynamo down"))
                : Mono.empty()));

        verify(client, timeout(WAIT_MS)).deleteMessage(deleteRequest("m-2"));
        verify(client, never()).deleteMessage(deleteRequest("m-1"));
        assertThat(processed).hasValue(2);
    }

    @Test
    void asksTheQueueWithLongPollingAndWithoutOverridingTheVisibilityTimeout() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(pendingPoll());

        start(counting(message -> Mono.empty()));

        verify(client, timeout(WAIT_MS)).receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(PROPERTIES.queueUrl())
                .maxNumberOfMessages(10)
                .waitTimeSeconds(20)
                .build());
    }

    @Test
    void keepsPollingAfterAFailedPoll() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("sqs down")),
                        withMessage("m-1"), pendingPoll());
        allowDeletes();

        start(counting(message -> Mono.empty()));

        verify(client, timeout(WAIT_MS)).deleteMessage(deleteRequest("m-1"));
    }

    @Test
    void recordsTheDurationOfEachMessageInTheMeterRegistry() {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(withMessage("m-1"), pendingPoll());
        allowDeletes();

        start(counting(message -> Mono.empty()));

        verify(client, timeout(WAIT_MS)).deleteMessage(deleteRequest("m-1"));
        assertThat(meterRegistry.find("async_operation.flow.duration")
                .tag("operation", "MessageFrom:" + PROPERTIES.queueUrl()).timer())
                .isNotNull()
                .satisfies(timer -> assertThat(timer.count()).isEqualTo(1));
    }

    @Test
    void stopWaitsForTheOpenPollInsteadOfAbandoningIt() throws InterruptedException {
        CompletableFuture<ReceiveMessageResponse> openPoll = pendingPoll();
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(openPoll);
        allowDeletes();
        start(counting(message -> Mono.empty()));
        verify(client, timeout(WAIT_MS)).receiveMessage(any(ReceiveMessageRequest.class));

        CountDownLatch stopped = new CountDownLatch(1);
        listener.stop(stopped::countDown);

        assertThat(listener.isRunning()).isFalse();
        assertThat(stopped.await(200, TimeUnit.MILLISECONDS)).isFalse();
        assertThat(openPoll).isNotCancelled();

        // The poll answers with a message: it is processed and deleted before the listener stops
        openPoll.complete(withMessage("m-1").join());

        assertThat(stopped.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        verify(client).deleteMessage(deleteRequest("m-1"));
        verify(client, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void stopWaitsForTheMessagesInProcessAndDeletesThem() throws InterruptedException {
        when(client.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(withMessage("m-1"), emptyPoll());
        allowDeletes();
        Sinks.Empty<Void> processing = Sinks.empty();
        start(counting(message -> processing.asMono()));
        waitUntilProcessed(1);

        CountDownLatch stopped = new CountDownLatch(1);
        listener.stop(stopped::countDown);

        assertThat(stopped.await(200, TimeUnit.MILLISECONDS)).isFalse();
        verify(client, never()).deleteMessage(any(DeleteMessageRequest.class));

        processing.tryEmitEmpty();

        assertThat(stopped.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        verify(client).deleteMessage(deleteRequest("m-1"));
    }

    @Test
    void doesNotPollAgainAfterStopping() throws InterruptedException {
        CompletableFuture<ReceiveMessageResponse> openPoll = pendingPoll();
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(openPoll);
        start(counting(message -> Mono.empty()));
        verify(client, timeout(WAIT_MS)).receiveMessage(any(ReceiveMessageRequest.class));

        CountDownLatch stopped = new CountDownLatch(1);
        listener.stop(stopped::countDown);
        openPoll.complete(ReceiveMessageResponse.builder().build());
        assertThat(stopped.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();

        // Still a single poll a while after stopping
        verify(client, after(200).times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    @Test
    void startAndStopAreSafeToCallTwice() throws InterruptedException {
        CompletableFuture<ReceiveMessageResponse> openPoll = pendingPoll();
        when(client.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(openPoll);
        start(counting(message -> Mono.empty()));
        listener.start();
        verify(client, timeout(WAIT_MS)).receiveMessage(any(ReceiveMessageRequest.class));
        assertThat(listener.isRunning()).isTrue();

        listener.stop();
        CountDownLatch stoppedAgain = new CountDownLatch(1);
        listener.stop(stoppedAgain::countDown);
        openPoll.complete(ReceiveMessageResponse.builder().build());

        assertThat(stoppedAgain.await(WAIT_MS, TimeUnit.MILLISECONDS)).isTrue();
        assertThat(listener.isRunning()).isFalse();
        verify(client, times(1)).receiveMessage(any(ReceiveMessageRequest.class));
    }

    private void start(Function<Message, Mono<Void>> processor) {
        listener = new SQSListener(client, PROPERTIES, processor, meterRegistry);
        listener.start();
    }

    private Function<Message, Mono<Void>> counting(Function<Message, Mono<Void>> result) {
        return message -> Mono.defer(() -> {
            processed.incrementAndGet();
            return result.apply(message);
        });
    }

    private void waitUntilProcessed(int count) {
        await().atMost(Duration.ofMillis(WAIT_MS)).untilAsserted(() -> assertThat(processed).hasValue(count));
    }

    private void allowDeletes() {
        when(client.deleteMessage(any(DeleteMessageRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(DeleteMessageResponse.builder().build()));
    }

    // A long poll that has not answered yet
    private static CompletableFuture<ReceiveMessageResponse> pendingPoll() {
        return new CompletableFuture<>();
    }

    private static CompletableFuture<ReceiveMessageResponse> emptyPoll() {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder().build());
    }

    private static CompletableFuture<ReceiveMessageResponse> withMessage(String id) {
        return CompletableFuture.completedFuture(ReceiveMessageResponse.builder()
                .messages(Message.builder().messageId(id).receiptHandle("r-" + id).body("{}").build())
                .build());
    }

    private static DeleteMessageRequest deleteRequest(String id) {
        return DeleteMessageRequest.builder()
                .queueUrl(PROPERTIES.queueUrl())
                .receiptHandle("r-" + id)
                .build();
    }
}

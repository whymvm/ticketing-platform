package co.com.ticketing.sqs.listener.helper;

import co.com.ticketing.sqs.listener.config.SQSProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.log4j.Log4j2;
import org.jspecify.annotations.NonNull;
import org.springframework.context.SmartLifecycle;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Polls an SQS queue and processes each message. A message is deleted only after it is processed successfully;
 * otherwise SQS delivers it again after the visibility timeout of the queue.
 * <p>
 * Spring starts the listener when the application is ready and stops it on shutdown. Stopping never abandons an
 * open poll: cancelling it on the client does not stop SQS from handing it a message that nobody would delete.
 * Instead, the listener stops asking for new messages, waits for the open poll to answer (at most the wait time),
 * processes and deletes what it brings, and then stops.
 */
@Log4j2
public class SQSListener implements SmartLifecycle {

    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
    private static final String METRIC_NAME = "async_operation";
    private static final String OPERATION_TAG = "operation";

    private final SqsAsyncClient client;
    private final SQSProperties properties;
    private final Function<Message, Mono<Void>> processor;
    private final MeterRegistry meterRegistry;
    private final String operation;

    private final AtomicBoolean running = new AtomicBoolean();
    private Sinks.Empty<Void> finished;
    private Scheduler scheduler;

    public SQSListener(SqsAsyncClient client, SQSProperties properties, Function<Message, Mono<Void>> processor,
                       MeterRegistry meterRegistry) {
        this.client = client;
        this.properties = properties;
        this.processor = processor;
        this.meterRegistry = meterRegistry;
        this.operation = "MessageFrom:" + properties.queueUrl();
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        finished = Sinks.empty();
        scheduler = Schedulers.newParallel("sqs-pool", properties.numberOfThreads());
        Flux.range(0, properties.numberOfThreads())
                .flatMap(_ -> pollUntilStopped().subscribeOn(scheduler))
                .doFinally(_ -> finished.tryEmitEmpty())
                .subscribe();
        log.info("Listening to {}", properties.queueUrl());
    }

    @Override
    public void stop(@NonNull Runnable callback) {
        if (!running.compareAndSet(true, false)) {
            callback.run();
            return;
        }
        log.info("Stopping the listener of {}: waiting for the open poll and the messages in process",
                properties.queueUrl());
        finished.asMono()
                .doFinally(_ -> {
                    scheduler.dispose();
                    log.info("Listener of {} stopped", properties.queueUrl());
                    callback.run();
                })
                .subscribe();
    }

    @Override
    public void stop() {
        stop(() -> { });
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    // Each cycle finishes its poll and its messages before deciding whether to poll again
    private Flux<Void> pollUntilStopped() {
        return receive()
                .flatMap(this::process, properties.maxNumberOfMessages())
                .repeat(running::get)
                // Keeps polling after a failed poll (for example, SQS briefly down) instead of stopping for good.
                // A wait in progress is not interrupted by a stop; Spring caps it with the shutdown phase timeout
                .retryWhen(Retry.backoff(Long.MAX_VALUE, FIRST_RETRY_DELAY)
                        .maxBackoff(MAX_RETRY_DELAY)
                        .transientErrors(true)
                        .filter(_ -> running.get())
                        .doBeforeRetry(signal -> log.error("Error polling the sqs queue", signal.failure())))
                .onErrorComplete();
    }

    private Flux<Message> receive() {
        return Mono.fromFuture(() -> client.receiveMessage(receiveRequest()))
                .doOnNext(response -> log.debug("{} received messages from sqs", response.messages().size()))
                .flatMapIterable(ReceiveMessageResponse::messages);
    }

    // An error is handled inside the message, so one failing message neither deletes it nor stops the polling
    private Mono<Void> process(Message message) {
        return processor.apply(message)
                .name(METRIC_NAME)
                .tag(OPERATION_TAG, operation)
                .tap(Micrometer.metrics(meterRegistry))
                .then(delete(message))
                .doOnError(error -> log.error("Error processing sqs message {}", message.messageId(), error))
                .onErrorResume(_ -> Mono.empty());
    }

    private Mono<Void> delete(Message message) {
        return Mono.fromFuture(() -> client.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(properties.queueUrl())
                        .receiptHandle(message.receiptHandle())
                        .build()))
                .then();
    }

    private ReceiveMessageRequest receiveRequest() {
        return ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxNumberOfMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .build();
    }
}

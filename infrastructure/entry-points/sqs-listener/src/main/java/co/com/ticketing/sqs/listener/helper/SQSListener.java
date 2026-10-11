package co.com.ticketing.sqs.listener.helper;

import co.com.ticketing.sqs.listener.config.SQSProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.Builder;
import lombok.extern.log4j.Log4j2;
import reactor.core.publisher.Flux;
import reactor.core.observability.micrometer.Micrometer;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.retry.Retry;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.util.function.Function;

@Log4j2
@Builder
public class SQSListener {
    private static final Duration FIRST_RETRY_DELAY = Duration.ofSeconds(1);
    private static final Duration MAX_RETRY_DELAY = Duration.ofSeconds(30);
    private static final String METRIC_NAME = "async_operation";
    private static final String OPERATION_TAG = "operation";

    private final SqsAsyncClient client;
    private final SQSProperties properties;
    private final Function<Message, Mono<Void>> processor;
    private final MeterRegistry meterRegistry;
    private String operation;

    public SQSListener start() {
        this.operation = "MessageFrom:" + properties.queueUrl();
        Scheduler scheduler = Schedulers.newParallel("sqs-pool", properties.numberOfThreads());
        for (var i = 0; i < properties.numberOfThreads(); i++) {
            listenRetryRepeat()
                    .subscribeOn(scheduler)
                    .subscribe();
        }
        return this;
    }

    private Flux<Void> listenRetryRepeat() {
        return listen()
                .doOnError(e -> log.error("Error listening sqs queue", e))
                .repeat()
                // Keeps listening after a failed poll (for example, SQS briefly down) instead of stopping for good
                .retryWhen(Retry.backoff(Long.MAX_VALUE, FIRST_RETRY_DELAY)
                        .maxBackoff(MAX_RETRY_DELAY)
                        .transientErrors(true));
    }

    private Flux<Void> listen() {
        return getMessages()
                .flatMap(message -> processor.apply(message)
                        .name(METRIC_NAME)
                        .tag(OPERATION_TAG, operation)
                        .tap(Micrometer.metrics(meterRegistry))
                        .then(confirm(message)),
                        properties.maxNumberOfMessages())
                .onErrorContinue((e, o) -> log.error("Error listening sqs message", e));
    }

    private Mono<Void> confirm(Message message) {
        return Mono.fromCallable(() -> getDeleteMessageRequest(message.receiptHandle()))
                .flatMap(request -> Mono.fromFuture(client.deleteMessage(request)))
                .then();
    }

    private Flux<Message> getMessages() {
        return Mono.fromCallable(this::getReceiveMessageRequest)
                .flatMap(request -> Mono.fromFuture(client.receiveMessage(request)))
                .doOnNext(response -> log.debug("{} received messages from sqs", response.messages().size()))
                .flatMapMany(response -> Flux.fromIterable(response.messages()));
    }

    private ReceiveMessageRequest getReceiveMessageRequest() {
        return ReceiveMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .maxNumberOfMessages(properties.maxNumberOfMessages())
                .waitTimeSeconds(properties.waitTimeSeconds())
                .build();
    }

    private DeleteMessageRequest getDeleteMessageRequest(String receiptHandle) {
        return DeleteMessageRequest.builder()
                .queueUrl(properties.queueUrl())
                .receiptHandle(receiptHandle)
                .build();
    }
}

package co.com.ticketing.sqs.listener;

import co.com.ticketing.usecase.reservetickets.ReserveTicketsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Function;

/**
 * Reserves the tickets of each placed order. If processing fails, the message is not deleted:
 * SQS delivers it again and, after several attempts, moves it to the dead-letter queue.
 */
@Service
@RequiredArgsConstructor
public class SQSProcessor implements Function<Message, Mono<Void>> {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final ReserveTicketsUseCase reserveTicketsUseCase;

    @Override
    public Mono<Void> apply(Message message) {
        return Mono.fromCallable(() -> JSON.readValue(message.body(), OrderPlacedMessage.class))
                .filter(OrderPlacedMessage::isValid)
                .switchIfEmpty(Mono.error(() -> new UnsupportedMessageException(message.messageId())))
                .flatMap(event -> reserveTicketsUseCase.run(event.orderId()));
    }
}

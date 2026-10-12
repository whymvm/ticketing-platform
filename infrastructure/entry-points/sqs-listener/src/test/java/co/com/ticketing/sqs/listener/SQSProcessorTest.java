package co.com.ticketing.sqs.listener;

import co.com.ticketing.usecase.reservetickets.ReserveTicketsUseCase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import software.amazon.awssdk.services.sqs.model.Message;
import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SQSProcessorTest {

    @Mock
    private ReserveTicketsUseCase reserveTicketsUseCase;

    @InjectMocks
    private SQSProcessor processor;

    @Test
    void reservesTheTicketsOfThePlacedOrder() {
        when(reserveTicketsUseCase.run("o-1")).thenReturn(Mono.empty());

        StepVerifier.create(processor.apply(message(
                        "{\"type\":\"ORDER_PLACED\",\"orderId\":\"o-1\",\"occurredAt\":\"2026-10-10T12:00:00Z\"}")))
                .verifyComplete();

        verify(reserveTicketsUseCase).run("o-1");
    }

    @Test
    void propagatesTheErrorsOfTheReservation() {
        when(reserveTicketsUseCase.run("o-1")).thenReturn(Mono.error(new IllegalStateException("dynamo down")));

        StepVerifier.create(processor.apply(message("{\"type\":\"ORDER_PLACED\",\"orderId\":\"o-1\"}")))
                .expectErrorMessage("dynamo down")
                .verify();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"type\":\"ORDER_CANCELLED\",\"orderId\":\"o-1\"}",
            "{\"type\":\"ORDER_PLACED\",\"orderId\":\" \"}",
            "{\"type\":\"ORDER_PLACED\"}"})
    void rejectsMessagesThatAreNotAValidOrderPlacedEvent(String body) {
        StepVerifier.create(processor.apply(message(body)))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(UnsupportedMessageException.class)
                        .hasMessage("message m-1 is not a valid ORDER_PLACED event"))
                .verify();

        verifyNoInteractions(reserveTicketsUseCase);
    }

    @Test
    void failsOnMalformedJson() {
        StepVerifier.create(processor.apply(message("not json")))
                .expectError(JacksonException.class)
                .verify();

        verifyNoInteractions(reserveTicketsUseCase);
    }

    private static Message message(String body) {
        return Message.builder().messageId("m-1").body(body).build();
    }
}

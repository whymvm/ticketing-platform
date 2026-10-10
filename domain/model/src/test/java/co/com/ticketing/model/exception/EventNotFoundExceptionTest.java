package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventNotFoundExceptionTest {

    @Test
    void namesTheMissingIdWithoutStackTrace() {
        EventNotFoundException error = new EventNotFoundException("e-1");

        assertThat(error).hasMessage("no event found with id e-1");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

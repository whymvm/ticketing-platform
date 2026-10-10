package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventAlreadyExistsExceptionTest {

    @Test
    void namesTheTakenIdWithoutStackTrace() {
        EventAlreadyExistsException error = new EventAlreadyExistsException("e-1");

        assertThat(error).hasMessage("an event with id e-1 already exists");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

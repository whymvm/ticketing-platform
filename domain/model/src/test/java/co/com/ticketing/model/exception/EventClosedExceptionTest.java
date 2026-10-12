package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EventClosedExceptionTest {

    @Test
    void namesTheEventWithoutStackTrace() {
        EventClosedException error = new EventClosedException("e-1");

        assertThat(error).hasMessage("event e-1 already started, so its sales are closed");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

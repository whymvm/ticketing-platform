package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderAlreadyExistsExceptionTest {

    @Test
    void namesTheTakenIdWithoutStackTrace() {
        OrderAlreadyExistsException error = new OrderAlreadyExistsException("o-1");

        assertThat(error).hasMessage("an order with id o-1 already exists");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

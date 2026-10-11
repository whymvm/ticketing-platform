package co.com.ticketing.model.exception;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OrderNotFoundExceptionTest {

    @Test
    void namesTheMissingIdWithoutStackTrace() {
        OrderNotFoundException error = new OrderNotFoundException("o-1");

        assertThat(error).hasMessage("no order found with id o-1");
        assertThat(error.getStackTrace()).isEmpty();
    }
}

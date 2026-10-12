package co.com.ticketing.model.order;

import co.com.ticketing.model.exception.InvalidCustomerException;
import co.com.ticketing.model.exception.InvalidOrderEventException;
import co.com.ticketing.model.exception.InvalidOrderQuantityException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderDetailsTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @ParameterizedTest
    @ValueSource(ints = {1, OrderDetails.MAX_TICKETS_PER_ORDER})
    void acceptsQuantitiesWithinTheLimits(int quantity) {
        assertThat(new OrderDetails("o-1", "e-1", "c-1", quantity, NOW).quantity()).isEqualTo(quantity);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, OrderDetails.MAX_TICKETS_PER_ORDER + 1})
    void rejectsQuantitiesOutsideTheLimits(Integer quantity) {
        assertThatThrownBy(() -> new OrderDetails("o-1", "e-1", "c-1", quantity, NOW))
                .isInstanceOf(InvalidOrderQuantityException.class)
                .hasMessage("quantity must be between 1 and 6 but was " + quantity);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingEvent(String eventId) {
        assertThatThrownBy(() -> new OrderDetails("o-1", eventId, "c-1", 1, NOW))
                .isInstanceOf(InvalidOrderEventException.class)
                .hasMessage("eventId is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingCustomer(String customerId) {
        assertThatThrownBy(() -> new OrderDetails("o-1", "e-1", customerId, 1, NOW))
                .isInstanceOf(InvalidCustomerException.class);
    }

    @Test
    void rejectsATooLongCustomer() {
        String customerId = "c".repeat(OrderDetails.MAX_CUSTOMER_ID_LENGTH + 1);

        assertThatThrownBy(() -> new OrderDetails("o-1", "e-1", customerId, 1, NOW))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage("customerId must not be blank and must have at most 64 characters");
    }

    @Test
    void orderExceptionsCarryNoStackTrace() {
        assertThatThrownBy(() -> new OrderDetails("o-1", "e-1", "c-1", 0, NOW))
                .satisfies(error -> assertThat(error.getStackTrace()).isEmpty());
    }
}

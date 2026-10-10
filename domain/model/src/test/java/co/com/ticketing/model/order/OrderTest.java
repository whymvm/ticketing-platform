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

class OrderTest {

    private static final Instant NOW = Instant.parse("2026-10-10T12:00:00Z");

    @Test
    void createQueuesTheOrder() {
        Order order = Order.create("o-1", new NewOrder("e-1", 2), "c-1", NOW);

        assertThat(order).isEqualTo(new Order("o-1", "e-1", "c-1", 2, OrderStatus.QUEUED, NOW));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, Order.MAX_TICKETS_PER_ORDER})
    void acceptsQuantitiesWithinTheLimits(int quantity) {
        assertThat(Order.create("o-1", new NewOrder("e-1", quantity), "c-1", NOW).quantity()).isEqualTo(quantity);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {0, -1, Order.MAX_TICKETS_PER_ORDER + 1})
    void rejectsQuantitiesOutsideTheLimits(Integer quantity) {
        assertThatThrownBy(() -> Order.create("o-1", new NewOrder("e-1", quantity), "c-1", NOW))
                .isInstanceOf(InvalidOrderQuantityException.class)
                .hasMessage("quantity must be between 1 and 6 but was " + quantity);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingEvent(String eventId) {
        assertThatThrownBy(() -> Order.create("o-1", new NewOrder(eventId, 1), "c-1", NOW))
                .isInstanceOf(InvalidOrderEventException.class)
                .hasMessage("eventId is required");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void rejectsAMissingCustomer(String customerId) {
        assertThatThrownBy(() -> Order.create("o-1", new NewOrder("e-1", 1), customerId, NOW))
                .isInstanceOf(InvalidCustomerException.class);
    }

    @Test
    void rejectsATooLongCustomer() {
        String customerId = "c".repeat(Order.MAX_CUSTOMER_ID_LENGTH + 1);

        assertThatThrownBy(() -> Order.create("o-1", new NewOrder("e-1", 1), customerId, NOW))
                .isInstanceOf(InvalidCustomerException.class)
                .hasMessage("customerId must not be blank and must have at most 64 characters");
    }

    @Test
    void orderExceptionsCarryNoStackTrace() {
        assertThatThrownBy(() -> Order.create("o-1", new NewOrder("e-1", 0), "c-1", NOW))
                .satisfies(error -> assertThat(error.getStackTrace()).isEmpty());
    }
}

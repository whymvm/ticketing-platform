package co.com.ticketing.model.event;

import co.com.ticketing.model.exception.InconsistentInventoryException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InventoryTest {

    @Test
    void initialInventoryHasEveryTicketAvailable() {
        assertThat(Inventory.initial(100)).isEqualTo(new Inventory(100, 0, 0, 0, 0));
    }

    @Test
    void totalAddsEveryCounter() {
        assertThat(new Inventory(50, 10, 5, 30, 5).total()).isEqualTo(100);
    }

    @Test
    void rejectsNegativeCounters() {
        assertThatThrownBy(() -> new Inventory(10, -1, 0, 0, 0))
                .isInstanceOf(InconsistentInventoryException.class)
                .hasMessage("inconsistent inventory: counters must be not negative");
    }

    @Test
    void rejectsMissingCounters() {
        assertThatThrownBy(() -> new Inventory(null, 0, 0, 0, 0))
                .isInstanceOf(InconsistentInventoryException.class)
                .hasMessage("inconsistent inventory: counters must be present");
    }
}

package co.com.ticketing.model.order;

/**
 * An order in one of its states. Each state is its own type and only has the transitions that are valid
 * from it, so an invalid transition does not compile.
 */
public sealed interface Order permits QueuedOrder, ReservedOrder, RejectedOrder {

    /**
     * Data shared by every state.
     */
    OrderDetails details();

    /**
     * Name of the state, for the API and the database.
     */
    OrderStatus status();
}

package dev.orderflow.order.order;

/**
 * The states an order can be in, and the transitions that are allowed.
 *
 * <p>Making the transition table explicit is what turns a duplicate event into a no-op instead
 * of a second payment: applying the same transition twice is simply not permitted, so it is
 * detected rather than silently repeated (Session 09).</p>
 */
public enum OrderStatus {

    /** Created, stock reserved, waiting for payment. */
    PENDING,

    /** Paid. The happy end state. */
    CONFIRMED,

    /** Shipped. */
    SHIPPED,

    /** Compensated: any reserved stock has been released. */
    CANCELLED;

    public boolean canMoveTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == SHIPPED || next == CANCELLED;
            case SHIPPED, CANCELLED -> false;   // terminal
        };
    }
}

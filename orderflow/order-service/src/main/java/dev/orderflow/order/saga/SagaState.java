package dev.orderflow.order.saga;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Where a saga is (Session 09).
 *
 * <p>The transition table is not decoration: it is what makes duplicate events harmless.
 * Applying {@code StockReserved} twice attempts {@code STOCK_RESERVED → STOCK_RESERVED}, which
 * is refused, so the second delivery cannot emit a second payment command. A saga without a
 * transition guard is a duplicate-charge incident waiting for a redelivery.</p>
 */
public enum SagaState {

    /** The order exists and stock is reserved; payment has been requested. */
    STARTED,

    /** Stock is held. */
    STOCK_RESERVED,

    /** Payment succeeded. */
    PAID,

    /** Everything went through. Terminal. */
    COMPLETED,

    /** Something failed; compensations are running. */
    COMPENSATING,

    /** Compensated: stock released, order cancelled. Terminal. */
    CANCELLED,

    /** Compensation itself failed repeatedly. Terminal, and needs a human (Session 09). */
    FAILED;

    private static final Map<SagaState, Set<SagaState>> ALLOWED = Map.of(
            STARTED, EnumSet.of(STOCK_RESERVED, COMPENSATING, FAILED),
            STOCK_RESERVED, EnumSet.of(PAID, COMPENSATING),
            PAID, EnumSet.of(COMPLETED, COMPENSATING),
            COMPENSATING, EnumSet.of(CANCELLED, FAILED),
            COMPLETED, EnumSet.noneOf(SagaState.class),
            CANCELLED, EnumSet.noneOf(SagaState.class),
            FAILED, EnumSet.noneOf(SagaState.class));

    public boolean canMoveTo(SagaState next) {
        return ALLOWED.get(this).contains(next);
    }

    public boolean isTerminal() {
        return ALLOWED.get(this).isEmpty();
    }
}

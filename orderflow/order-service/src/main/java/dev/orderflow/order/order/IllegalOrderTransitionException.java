package dev.orderflow.order.order;

import java.util.UUID;

/**
 * Someone tried to move an order to a state it cannot reach from where it is.
 *
 * <p>In a distributed system this is usually not a bug in the caller: it is a <b>duplicate
 * event</b> arriving after the first one was applied. Handlers catch it and treat it as
 * « already done » rather than as a failure (Sessions 08 and 09).</p>
 */
public class IllegalOrderTransitionException extends RuntimeException {

    public IllegalOrderTransitionException(UUID orderId, OrderStatus from, OrderStatus to) {
        super("Order %s cannot move from %s to %s".formatted(orderId, from, to));
    }
}

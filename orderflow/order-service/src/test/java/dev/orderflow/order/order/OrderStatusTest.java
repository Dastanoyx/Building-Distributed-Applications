package dev.orderflow.order.order;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The transition table, tested on its own (Session 09).
 *
 * <p>These four tests are what stop a redelivered event from confirming an order twice. They
 * need no Spring, no Kafka and no database, and they run in single-digit milliseconds.</p>
 */
class OrderStatusTest {

    @Test
    void a_pending_order_can_be_confirmed_or_cancelled() {
        assertTrue(OrderStatus.PENDING.canMoveTo(OrderStatus.CONFIRMED));
        assertTrue(OrderStatus.PENDING.canMoveTo(OrderStatus.CANCELLED));
    }

    @Test
    void a_cancelled_order_is_terminal() {
        assertFalse(OrderStatus.CANCELLED.canMoveTo(OrderStatus.CONFIRMED));
        assertFalse(OrderStatus.CANCELLED.canMoveTo(OrderStatus.SHIPPED));
    }

    @Test
    void confirming_twice_is_refused_which_is_how_duplicates_are_caught() {
        Order order = sampleOrder();
        order.confirm();

        assertThrows(IllegalOrderTransitionException.class, order::confirm);
    }

    @Test
    void cancelling_records_the_reason() {
        Order order = sampleOrder();
        order.cancel("payment declined");

        assertTrue(order.isIn(OrderStatus.CANCELLED));
        assertTrue(order.getCancellationReason().contains("declined"));
    }

    private Order sampleOrder() {
        return Order.pending(UUID.randomUUID(), "c-1",
                List.of(new Order.NewLine("KEY-001", 2, new BigDecimal("129.00"))),
                new BigDecimal("258.00"));
    }
}

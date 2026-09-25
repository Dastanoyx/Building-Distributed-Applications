package dev.orderflow.order.saga;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The saga's transition guard — the thing that makes duplicate events harmless (Session 09). */
class SagaStateTest {

    @Test
    void the_happy_path_is_allowed() {
        assertTrue(SagaState.STARTED.canMoveTo(SagaState.STOCK_RESERVED));
        assertTrue(SagaState.STOCK_RESERVED.canMoveTo(SagaState.PAID));
        assertTrue(SagaState.PAID.canMoveTo(SagaState.COMPLETED));
    }

    @Test
    void compensation_is_reachable_from_every_running_state() {
        assertTrue(SagaState.STARTED.canMoveTo(SagaState.COMPENSATING));
        assertTrue(SagaState.STOCK_RESERVED.canMoveTo(SagaState.COMPENSATING));
        assertTrue(SagaState.PAID.canMoveTo(SagaState.COMPENSATING));
    }

    @Test
    void terminal_states_go_nowhere() {
        assertTrue(SagaState.COMPLETED.isTerminal());
        assertTrue(SagaState.CANCELLED.isTerminal());
        assertTrue(SagaState.FAILED.isTerminal());
        assertFalse(SagaState.STOCK_RESERVED.isTerminal());
    }

    @Test
    void applying_the_same_transition_twice_is_refused() {
        OrderSaga saga = OrderSaga.start(UUID.randomUUID());
        saga.stockReserved();

        // This is exactly what a redelivered StockReserved event would attempt.
        assertThrows(OrderSaga.IllegalSagaTransitionException.class, saga::stockReserved);
        assertEquals(SagaState.STOCK_RESERVED, saga.getState());
    }

    @Test
    void a_failed_compensation_can_always_escalate() {
        OrderSaga saga = OrderSaga.start(UUID.randomUUID());
        saga.compensate("payment declined");
        saga.failed("compensation did not complete");

        assertEquals(SagaState.FAILED, saga.getState());
        assertTrue(saga.getFailureReason().contains("compensation"));
    }
}

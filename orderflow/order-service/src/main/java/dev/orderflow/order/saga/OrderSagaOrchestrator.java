package dev.orderflow.order.saga;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.orderflow.events.OrderEvent;
import dev.orderflow.events.PaymentMessages;
import dev.orderflow.events.Topics;
import dev.orderflow.order.client.CatalogClient;
import dev.orderflow.order.order.Order;
import dev.orderflow.order.order.OrderRepository;
import dev.orderflow.order.order.OrderStatus;
import dev.orderflow.order.outbox.OutboxPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The saga coordinator (Session 09).
 *
 * <p>An order spans three services and three databases, so there is no transaction that can
 * roll them all back. Instead the workflow is a sequence of local transactions, each with a
 * compensating action:</p>
 *
 * <pre>
 *   create order  →  reserve stock  →  take payment  →  CONFIRMED
 *                         ↓ (payment failed)
 *                   release stock   →  cancel order  →  CANCELLED
 * </pre>
 *
 * <p>Every handler is <b>idempotent</b>: a redelivered event finds the saga already in the
 * target state and returns without emitting anything. That is what makes at-least-once
 * delivery safe (Session 08).</p>
 */
@Component
public class OrderSagaOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(OrderSagaOrchestrator.class);

    /** After this long without progress, a saga is considered stuck and is retried or compensated. */
    private static final Duration STUCK_AFTER = Duration.ofMinutes(2);

    /** How many times a stuck saga is nudged before we give up and compensate. */
    private static final int MAX_ATTEMPTS = 3;

    private final OrderSagaRepository sagas;
    private final OrderRepository orders;
    private final OutboxPublisher outbox;
    private final CatalogClient catalog;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meters;

    public OrderSagaOrchestrator(OrderSagaRepository sagas, OrderRepository orders, OutboxPublisher outbox,
                                 CatalogClient catalog, ObjectMapper objectMapper, MeterRegistry meters) {
        this.sagas = sagas;
        this.orders = orders;
        this.outbox = outbox;
        this.catalog = catalog;
        this.objectMapper = objectMapper;
        this.meters = meters;
        registerGauges();
    }

    /**
     * Step 2 → 3. The order was placed and the stock is held, so ask for payment.
     *
     * <p>Triggered by our own {@code OrderPlaced} event rather than called directly from
     * {@code OrderService}: the saga then starts from a fact that is already committed, so a
     * crash between the two can never lose the step.</p>
     */
    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "order-service-saga")
    @Transactional
    public void onOrderEvent(String payload) throws Exception {
        OrderEvent event = objectMapper.readValue(payload, OrderEvent.class);
        if (!(event instanceof OrderEvent.OrderPlaced placed)) {
            return;   // we only react to placements here
        }

        OrderSaga saga = sagas.findById(placed.orderId()).orElse(null);
        if (saga == null) {
            log.warn("no saga for order {} — ignoring", placed.orderId());
            return;
        }
        if (!saga.isIn(SagaState.STARTED)) {
            log.debug("duplicate OrderPlaced for {} ignored (saga is {})", placed.orderId(), saga.getState());
            return;
        }

        saga.stockReserved();   // the stock was already reserved synchronously before the order was saved
        outbox.publish(Topics.PAYMENT_COMMANDS, "Order", placed.orderId().toString(),
                PaymentMessages.ProcessPayment.of(placed.orderId(), placed.customerId(), placed.total()));
        log.info("saga {} → STOCK_RESERVED, payment requested", placed.orderId());
    }

    /** Step 3 → done. Payment succeeded: confirm the order and announce it. */
    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = "order-service-saga")
    @Transactional
    public void onPaymentEvent(String payload) throws Exception {
        if (payload.contains("\"authorisationCode\"")) {
            PaymentMessages.PaymentSucceeded event =
                    objectMapper.readValue(payload, PaymentMessages.PaymentSucceeded.class);
            onPaymentSucceeded(event);
        } else {
            PaymentMessages.PaymentFailed event =
                    objectMapper.readValue(payload, PaymentMessages.PaymentFailed.class);
            onPaymentFailed(event);
        }
    }

    void onPaymentSucceeded(PaymentMessages.PaymentSucceeded event) {
        OrderSaga saga = sagas.findById(event.orderId()).orElse(null);
        if (saga == null || saga.getState().isTerminal()) {
            log.debug("PaymentSucceeded for {} ignored (saga absent or terminal)", event.orderId());
            return;
        }
        if (!saga.isIn(SagaState.STOCK_RESERVED)) {
            log.debug("duplicate PaymentSucceeded for {} ignored", event.orderId());
            return;
        }

        saga.paid();
        Order order = orders.findWithLinesById(event.orderId()).orElseThrow();
        order.confirm();
        saga.completed();

        outbox.publish(Topics.ORDER_EVENTS, "Order", order.getId().toString(),
                OrderEvent.OrderConfirmed.of(order.getId(), order.getCustomerId(), order.getTotal()));
        log.info("saga {} → COMPLETED, order CONFIRMED", event.orderId());
    }

    /**
     * Payment failed: run the compensations in reverse order.
     *
     * <p>Releasing the stock is done through the catalog API here; the order is cancelled
     * locally, and the cancellation is announced through the outbox so the notification service
     * hears about it exactly like any other fact.</p>
     */
    void onPaymentFailed(PaymentMessages.PaymentFailed event) {
        OrderSaga saga = sagas.findById(event.orderId()).orElse(null);
        if (saga == null || saga.getState().isTerminal()) {
            return;
        }
        if (saga.isIn(SagaState.COMPENSATING)) {
            log.debug("duplicate PaymentFailed for {} ignored", event.orderId());
            return;
        }

        saga.compensate(event.reason());
        catalog.release(event.orderId());                       // compensation of step 2

        Order order = orders.findWithLinesById(event.orderId()).orElseThrow();
        if (!order.isIn(OrderStatus.CANCELLED)) {
            order.cancel(event.reason());                       // compensation of step 1
        }
        saga.cancelled();

        outbox.publish(Topics.ORDER_EVENTS, "Order", order.getId().toString(),
                OrderEvent.OrderCancelled.of(order.getId(), order.getCustomerId(), event.reason()));
        meters.counter("orderflow.orders.rejected", "reason", "payment_declined").increment();
        log.info("saga {} → CANCELLED (compensated): {}", event.orderId(), event.reason());
    }

    /**
     * Rescues sagas that stopped moving (Session 09 and 13).
     *
     * <p>Messages get lost and instances die between two steps. Without this sweeper the stock
     * of such an order stays reserved forever and nobody notices. With it, the saga is nudged a
     * few times, then compensated, then escalated to a human — because « retry forever » is not
     * a recovery strategy.</p>
     *
     * <p><b>Important:</b> this job PUBLISHES, so running it on three replicas would emit three
     * payment commands. It must be guarded by a distributed lock (ShedLock) — see Session 13 and
     * {@code docs/scheduled-jobs.md}.</p>
     */
    @org.springframework.scheduling.annotation.Scheduled(
            fixedDelayString = "${orderflow.saga.sweep-interval-ms:30000}")
    @Transactional
    public void sweepStuckSagas() {
        Instant threshold = Instant.now().minus(STUCK_AFTER);
        List<OrderSaga> stuck = sagas.findByStateInAndLastTransitionAtBefore(
                EnumSet.of(SagaState.STARTED, SagaState.STOCK_RESERVED, SagaState.COMPENSATING), threshold);

        for (OrderSaga saga : stuck) {
            log.warn("saga {} stuck in {} for {}", saga.getOrderId(), saga.getState(), saga.stuckFor());

            if (saga.getAttempts() < MAX_ATTEMPTS) {
                saga.retried();
                reissuePendingCommand(saga);
            } else if (!saga.isIn(SagaState.COMPENSATING)) {
                saga.compensate("timeout waiting in " + saga.getState());
                catalog.release(saga.getOrderId());
                orders.findWithLinesById(saga.getOrderId())
                        .filter(order -> !order.isIn(OrderStatus.CANCELLED))
                        .ifPresent(order -> order.cancel("saga timeout"));
                saga.cancelled();
            } else {
                // Compensation itself is failing: stop, alert, let a human decide.
                saga.failed("compensation did not complete after " + MAX_ATTEMPTS + " attempts");
                meters.counter("orderflow.saga.failed").increment();
                log.error("saga {} needs manual attention: {}", saga.getOrderId(), saga.getFailureReason());
            }
        }
    }

    private void reissuePendingCommand(OrderSaga saga) {
        orders.findWithLinesById(saga.getOrderId()).ifPresent(order -> {
            if (saga.isIn(SagaState.STOCK_RESERVED)) {
                outbox.publish(Topics.PAYMENT_COMMANDS, "Order", order.getId().toString(),
                        PaymentMessages.ProcessPayment.of(order.getId(), order.getCustomerId(), order.getTotal()));
                log.info("re-issued ProcessPayment for saga {}", saga.getOrderId());
            }
        });
    }

    /** One gauge per state, so « how many sagas are compensating right now » is a graph. */
    private void registerGauges() {
        for (SagaState state : SagaState.values()) {
            io.micrometer.core.instrument.Gauge
                    .builder("orderflow.saga.count", sagas, repository -> repository.countByState(state))
                    .tag("state", state.name())
                    .description("Sagas currently in this state")
                    .register(meters);
        }
    }

    /** Exposed for tests: lets a test drive a transition without going through Kafka. */
    UUID orderIdOf(OrderSaga saga) {
        return saga.getOrderId();
    }
}

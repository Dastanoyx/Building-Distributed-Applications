package dev.orderflow.order.order;

import dev.orderflow.events.OrderEvent;
import dev.orderflow.events.Topics;
import dev.orderflow.order.client.CatalogClient;
import dev.orderflow.order.client.CatalogExceptions;
import dev.orderflow.order.order.dto.OrderResponse;
import dev.orderflow.order.order.dto.PlaceOrderRequest;
import dev.orderflow.order.outbox.OutboxPublisher;
import dev.orderflow.order.saga.OrderSaga;
import dev.orderflow.order.saga.OrderSagaRepository;
import dev.orderflow.order.shared.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Placing and reading orders — the use cases of this service.
 *
 * <p>{@link #place} is the method where most of the course meets:</p>
 * <ol>
 *   <li>the order id is generated <b>first</b>, so it can serve as the idempotency key of the
 *       reservation (Session 05);</li>
 *   <li>the catalog is called synchronously, because the answer decides the response — and that
 *       call is wrapped in timeouts, retries and a circuit breaker (Session 06);</li>
 *   <li>the order, the saga and the event are written in <b>one</b> transaction through the
 *       outbox, so « saved » and « announced » cannot disagree (Session 09).</li>
 * </ol>
 *
 * <p>Note what is deliberately absent: no call to Kafka, no HTTP call inside the transaction
 * except the reservation, and no compensation logic — that belongs to the orchestrator.</p>
 */
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final OrderSagaRepository sagas;
    private final CatalogClient catalog;
    private final OutboxPublisher outbox;
    private final Counter placedCounter;
    private final MeterRegistry meters;

    public OrderService(OrderRepository orders, OrderSagaRepository sagas, CatalogClient catalog,
                        OutboxPublisher outbox, MeterRegistry meters) {
        this.orders = orders;
        this.sagas = sagas;
        this.catalog = catalog;
        this.outbox = outbox;
        this.meters = meters;
        this.placedCounter = Counter.builder("orderflow.orders.placed")
                .description("Orders accepted")
                .register(meters);
    }

    /**
     * Places an order: reserves the stock, stores the order, queues the event.
     *
     * @throws CatalogExceptions.StockUnavailable   mapped to 409 by the exception handler
     * @throws CatalogExceptions.ProductNotFound    mapped to 400 — the caller sent a bad SKU
     * @throws CatalogExceptions.CatalogUnavailable mapped to 503 with Retry-After. The order is
     *         NOT created: refusing honestly beats inventing a reservation (Session 06)
     */
    @Transactional
    public OrderResponse place(PlaceOrderRequest request) {
        UUID orderId = UUID.randomUUID();

        List<CatalogClient.ReservationLine> reservationLines = request.lines().stream()
                .map(line -> new CatalogClient.ReservationLine(line.sku(), line.quantity()))
                .toList();

        // Synchronous, because the answer changes what we reply to the user.
        CatalogExceptions.Reservation reservation = catalog.reserve(orderId, reservationLines);

        // The catalog owns prices, so the unit price comes back from it. Here we spread the
        // total over the lines; a richer catalog response would carry the per-line price.
        BigDecimal total = reservation.totalPrice();
        List<Order.NewLine> lines = request.lines().stream()
                .map(line -> new Order.NewLine(line.sku(), line.quantity(),
                        unitPriceOf(total, request)))
                .toList();

        Order order = orders.save(Order.pending(orderId, request.customerId(), lines, total));
        sagas.save(OrderSaga.start(orderId));

        // Not a Kafka call: a row in the same transaction (Session 09).
        outbox.publish(Topics.ORDER_EVENTS, "Order", orderId.toString(),
                OrderEvent.OrderPlaced.of(orderId, request.customerId(),
                        order.getLines().stream()
                                .map(l -> new OrderEvent.Line(l.getSku(), l.getQuantity(), l.getUnitPrice()))
                                .toList(),
                        total));

        placedCounter.increment();
        log.info("order {} placed for customer {}, total {}", orderId, request.customerId(), total);
        return OrderResponse.from(order);
    }

    /**
     * Reads one order, enriched with product names when the catalog is reachable.
     *
     * <p>This is the documented degraded mode: if the catalog is down the order is still
     * returned, with {@code enriched=false}. A read that fails entirely because a <b>decorative</b>
     * dependency is unavailable is a design mistake (Session 06).</p>
     */
    @Transactional(readOnly = true)
    public OrderResponse view(UUID orderId) {
        Order order = orders.findWithLinesById(orderId)
                .orElseThrow(() -> new NotFoundException("Order %s not found".formatted(orderId)));
        try {
            Map<String, String> names = Map.of();   // a richer client would batch-fetch names here
            return names.isEmpty() ? OrderResponse.from(order) : OrderResponse.enriched(order, names);
        } catch (RuntimeException e) {
            meters.counter("orderflow.orders.degraded_reads").increment();
            log.warn("serving order {} without catalog enrichment: {}", orderId, e.toString());
            return OrderResponse.from(order);
        }
    }

    @Transactional(readOnly = true)
    public Page<OrderResponse> byCustomer(String customerId, Pageable pageable) {
        return orders.findByCustomerId(customerId, pageable).map(OrderResponse::from);
    }

    /** Loads an order or fails; used by the saga orchestrator. */
    @Transactional(readOnly = true)
    public Order require(UUID orderId) {
        return orders.findWithLinesById(orderId)
                .orElseThrow(() -> new NotFoundException("Order %s not found".formatted(orderId)));
    }

    private BigDecimal unitPriceOf(BigDecimal total, PlaceOrderRequest request) {
        int units = request.lines().stream().mapToInt(PlaceOrderRequest.Line::quantity).sum();
        return units == 0 ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(units), 2, java.math.RoundingMode.HALF_UP);
    }
}

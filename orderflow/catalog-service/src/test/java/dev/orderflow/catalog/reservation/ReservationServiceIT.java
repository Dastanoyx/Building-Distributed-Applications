package dev.orderflow.catalog.reservation;

import dev.orderflow.catalog.product.Product;
import dev.orderflow.catalog.product.ProductRepository;
import dev.orderflow.catalog.reservation.dto.ReservationRequest;
import dev.orderflow.catalog.reservation.dto.ReservationResponse;
import dev.orderflow.catalog.shared.InsufficientStockException;
import dev.orderflow.catalog.shared.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration tests against a real PostgreSQL started by Testcontainers (Session 03).
 *
 * <p>The container is {@code static}, so it starts once for the whole class. Making it
 * non-static starts one per test method and turns a nine-second class into forty.</p>
 *
 * <p>{@code @ServiceConnection} wires the datasource automatically — no property juggling —
 * and Flyway runs the real migrations, so the tests exercise the schema that ships.</p>
 */
@SpringBootTest
@Testcontainers
class ReservationServiceIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private ReservationService service;

    @Autowired
    private ProductRepository products;

    @Autowired
    private ReservationRepository reservations;

    @BeforeEach
    void seed() {
        reservations.deleteAll();
        products.deleteAll();
        products.save(new Product("KEY-001", "Mechanical keyboard", new BigDecimal("129.00"), 10));
    }

    private ReservationRequest request(UUID orderId, int quantity) {
        return new ReservationRequest(orderId, List.of(new ReservationRequest.Line("KEY-001", quantity)));
    }

    @Test
    void reserving_holds_stock_and_prices_the_order() {
        ReservationResponse response = service.reserve(request(UUID.randomUUID(), 3));

        assertEquals("HELD", response.status());
        assertEquals(new BigDecimal("387.00"), response.totalPrice());
        assertEquals(7, products.findBySku("KEY-001").orElseThrow().getStock());
    }

    @Test
    void reserving_more_than_available_is_refused_and_changes_nothing() {
        assertThrows(InsufficientStockException.class, () -> service.reserve(request(UUID.randomUUID(), 11)));
        assertEquals(10, products.findBySku("KEY-001").orElseThrow().getStock());
    }

    @Test
    void an_unknown_sku_is_reported_as_not_found() {
        ReservationRequest bad = new ReservationRequest(UUID.randomUUID(),
                List.of(new ReservationRequest.Line("ZZZ-999", 1)));
        assertThrows(NotFoundException.class, () -> service.reserve(bad));
    }

    @Test
    void releasing_restores_stock_and_is_safe_to_repeat() {
        UUID orderId = UUID.randomUUID();
        service.reserve(request(orderId, 4));

        service.release(orderId);
        service.release(orderId);   // a redelivered compensation must not inflate the stock

        assertEquals(10, products.findBySku("KEY-001").orElseThrow().getStock());
    }

    @Test
    void reserving_twice_for_the_same_order_reserves_once() {
        UUID orderId = UUID.randomUUID();

        ReservationResponse first = service.reserve(request(orderId, 3));
        ReservationResponse second = service.reserve(request(orderId, 3));

        assertEquals("HELD", first.status());
        assertEquals("ALREADY_HELD", second.status());
        assertEquals(7, products.findBySku("KEY-001").orElseThrow().getStock());
    }

    /**
     * The test that fails on a naive read-then-write implementation.
     *
     * <p>Ten threads each try to take 2 units out of a stock of 10. Exactly five must succeed.
     * A version that loads the product, checks the stock in Java and saves it back typically
     * lets seven to ten through and ends with a negative stock — or a CHECK violation, which
     * is precisely why that constraint exists.</p>
     */
    @Test
    void concurrent_reservations_never_oversell() throws Exception {
        AtomicInteger succeeded = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newFixedThreadPool(10)) {
            List<Future<?>> attempts = IntStream.range(0, 10)
                    .mapToObj(i -> pool.submit(() -> {
                        start.await();
                        try {
                            service.reserve(request(UUID.randomUUID(), 2));
                            succeeded.incrementAndGet();
                        } catch (InsufficientStockException expected) {
                            // half of the callers are supposed to lose
                        }
                        return null;
                    }))
                    .toList();

            start.countDown();
            for (Future<?> attempt : attempts) {
                attempt.get();
            }
        }

        assertEquals(5, succeeded.get(), "10 units, 2 per request: exactly five may win");
        assertEquals(0, products.findBySku("KEY-001").orElseThrow().getStock());
    }
}

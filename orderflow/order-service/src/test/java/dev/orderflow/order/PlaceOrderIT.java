package dev.orderflow.order;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import dev.orderflow.order.order.OrderRepository;
import dev.orderflow.order.order.OrderStatus;
import dev.orderflow.order.outbox.OutboxRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end through the order service, with a real database and a scripted catalog
 * (Sessions 03, 05, 06 and 09).
 *
 * <p>PostgreSQL is real because the SQL, the migrations and the transactions are exactly what
 * a mocked repository would fail to exercise. The catalog is a WireMock stub because this test
 * is about <b>our</b> behaviour, and a second real service would make it slow and flaky.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PlaceOrderIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static WireMockServer catalog = new WireMockServer(0);

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private OrderRepository orders;

    @Autowired
    private OutboxRepository outbox;

    @BeforeAll
    static void startCatalog() {
        catalog.start();
    }

    @AfterAll
    static void stopCatalog() {
        catalog.stop();
    }

    /** Points the service at the stub, and disables Kafka so the test needs no broker. */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("orderflow.catalog.base-url", () -> "http://localhost:" + catalog.port());
        registry.add("spring.kafka.bootstrap-servers", () -> "localhost:59092");
        registry.add("spring.kafka.listener.auto-startup", () -> "false");
        registry.add("orderflow.outbox.drain-interval-ms", () -> "600000");   // do not drain during tests
    }

    @BeforeEach
    void reset() {
        catalog.resetAll();
        outbox.deleteAll();
        orders.deleteAll();
    }

    private ResponseEntity<String> place(String idempotencyKey, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange("/api/orders", HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private static final String ONE_KEYBOARD = """
            {"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":2}]}
            """;

    @Test
    void placing_an_order_reserves_stock_saves_the_order_and_queues_exactly_one_event() {
        catalog.stubFor(post(urlEqualTo("/api/reservations")).willReturn(aResponse()
                .withStatus(201)
                .withHeader("Content-Type", "application/json")
                .withBody("""
                          {"orderId":"00000000-0000-0000-0000-000000000000",
                           "status":"HELD","totalPrice":258.00}
                          """)));

        ResponseEntity<String> response = place(UUID.randomUUID().toString(), ONE_KEYBOARD);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertEquals(1, orders.count());
        // The event is in the outbox, NOT in Kafka: same transaction as the order (Session 09).
        assertEquals(1, outbox.countByPublishedAtIsNull());
        assertEquals(OrderStatus.PENDING, orders.findAll().get(0).getStatus());
    }

    @Test
    void the_same_idempotency_key_twice_creates_one_order_and_calls_the_catalog_once() {
        catalog.stubFor(post(urlEqualTo("/api/reservations")).willReturn(aResponse()
                .withStatus(201).withHeader("Content-Type", "application/json")
                .withBody("""
                          {"orderId":"00000000-0000-0000-0000-000000000000",
                           "status":"HELD","totalPrice":258.00}
                          """)));

        String key = UUID.randomUUID().toString();
        ResponseEntity<String> first = place(key, ONE_KEYBOARD);
        ResponseEntity<String> second = place(key, ONE_KEYBOARD);

        assertEquals(HttpStatus.CREATED, first.getStatusCode());
        assertEquals(HttpStatus.CREATED, second.getStatusCode());   // replayed, not recreated
        assertEquals(1, orders.count(), "a retried request must not create a second order");
        catalog.verify(1, postRequestedFor(urlEqualTo("/api/reservations")));
    }

    @Test
    void insufficient_stock_is_a_409_and_creates_nothing() {
        catalog.stubFor(post(urlEqualTo("/api/reservations")).willReturn(aResponse()
                .withStatus(409).withHeader("Content-Type", "application/problem+json")
                .withBody("""
                          {"title":"Insufficient stock","status":409,"sku":"KEY-001",
                           "requested":2,"available":1}
                          """)));

        ResponseEntity<String> response = place(UUID.randomUUID().toString(), ONE_KEYBOARD);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertTrue(response.getBody().contains("available"));
        assertEquals(0, orders.count());
    }

    @Test
    void a_catalog_outage_returns_503_with_retry_after_and_never_a_phantom_order() {
        catalog.stubFor(post(urlEqualTo("/api/reservations")).willReturn(aResponse().withStatus(503)));

        ResponseEntity<String> response = place(UUID.randomUUID().toString(), ONE_KEYBOARD);

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
        assertEquals("5", response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        assertEquals(0, orders.count(), "no order may exist without a reservation");
        // The retry policy tried three times before giving up (Session 06).
        catalog.verify(3, postRequestedFor(urlEqualTo("/api/reservations")));
        WireMock.reset();
    }

    @Test
    void a_missing_idempotency_key_is_rejected() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        ResponseEntity<String> response = rest.exchange("/api/orders", HttpMethod.POST,
                new HttpEntity<>(ONE_KEYBOARD, headers), String.class);

        assertTrue(response.getStatusCode().is4xxClientError());
        assertEquals(0, orders.count());
    }
}

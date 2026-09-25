package dev.orderflow.order.client;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything this service knows about the catalog, in one class (Session 05 and 06).
 *
 * <p>The method looks like a local call, which is exactly the danger the fallacies warn about.
 * So three protections are wrapped around it:</p>
 * <ul>
 *   <li><b>Timeouts</b> — configured on the {@code RestClient}, so a slow catalog fails fast.</li>
 *   <li><b>{@link Retry}</b> — a couple of attempts with backoff and jitter, and only for
 *       technical failures. It is safe <i>only</i> because every attempt carries the same
 *       idempotency key: the catalog recognises the duplicate and replays its first answer.</li>
 *   <li><b>{@link CircuitBreaker}</b> — after enough failures, stop calling entirely and answer
 *       immediately, which protects this service's threads and gives the catalog room to recover.</li>
 * </ul>
 *
 * <p>Business refusals (404, 409) are re-thrown untouched by the fallback so they are neither
 * retried nor counted as failures.</p>
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private final RestClient restClient;

    public CatalogClient(RestClient catalogRestClient) {
        this.restClient = catalogRestClient;
    }

    /**
     * Reserves stock for a whole order in one call.
     *
     * @param orderId doubles as the idempotency key: retrying is safe because the catalog
     *                recognises an order it has already reserved
     * @throws CatalogExceptions.ProductNotFound    a SKU does not exist (caller error, 400 upstream)
     * @throws CatalogExceptions.StockUnavailable   valid request, not enough stock (409 upstream)
     * @throws CatalogExceptions.CatalogUnavailable the catalog is down or too slow (503 upstream)
     */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog", fallbackMethod = "reserveFallback")
    public CatalogExceptions.Reservation reserve(UUID orderId, List<ReservationLine> lines) {
        log.debug("reserving {} line(s) for order {}", lines.size(), orderId);

        JsonNode body = restClient.post()
                .uri("/api/reservations")
                .contentType(MediaType.APPLICATION_JSON)
                .header("Idempotency-Key", orderId.toString())
                .body(Map.of("orderId", orderId, "lines", lines))
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> {
                    throw translate(response.getStatusCode().value(), read(response.getBody()));
                })
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> {
                    throw new CatalogExceptions.CatalogUnavailable(
                            "catalog returned " + response.getStatusCode());
                })
                .body(JsonNode.class);

        return new CatalogExceptions.Reservation(
                body.path("status").asText("HELD"),
                new BigDecimal(body.path("totalPrice").asText("0")));
    }

    /**
     * Releases every reservation of an order — the compensating action (Session 09).
     *
     * <p>Failure here is logged and swallowed on purpose: the saga will retry through its
     * sweeper, and letting this exception escape would abort a compensation that must
     * eventually complete.</p>
     */
    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog", fallbackMethod = "releaseFallback")
    public void release(UUID orderId) {
        restClient.delete()
                .uri("/api/reservations/{orderId}", orderId)
                .retrieve()
                .toBodilessEntity();
        log.info("released the reservation of order {}", orderId);
    }

    /** One line of a reservation request. */
    public record ReservationLine(String sku, int quantity) {
    }

    // ------------------------------------------------------------------ fallbacks

    @SuppressWarnings("unused")   // called by Resilience4j
    private CatalogExceptions.Reservation reserveFallback(UUID orderId, List<ReservationLine> lines,
                                                          Exception failure) {
        if (failure instanceof CatalogExceptions.StockUnavailable
                || failure instanceof CatalogExceptions.ProductNotFound) {
            throw (RuntimeException) failure;   // a business answer, not an outage
        }
        log.warn("catalog unavailable while reserving order {}: {}", orderId, failure.toString());
        throw new CatalogExceptions.CatalogUnavailable("catalog unavailable", failure);
    }

    @SuppressWarnings("unused")
    private void releaseFallback(UUID orderId, Exception failure) {
        // Do not rethrow: the saga sweeper retries the compensation later (Session 09).
        log.error("could not release the reservation of order {} yet: {}", orderId, failure.toString());
    }

    // ------------------------------------------------------------------ helpers

    private RuntimeException translate(int status, JsonNode problem) {
        String sku = problem.path("sku").asText(null);
        return switch (status) {
            case 404 -> new CatalogExceptions.ProductNotFound(sku == null ? "unknown" : sku);
            case 409 -> new CatalogExceptions.StockUnavailable(
                    sku == null ? "unknown" : sku,
                    problem.path("requested").asInt(0),
                    problem.path("available").asInt(0));
            // 400 and friends: our request was wrong. Not retryable, not the catalog's fault.
            default -> new IllegalStateException("catalog rejected the request: "
                    + problem.path("detail").asText("HTTP " + status));
        };
    }

    private JsonNode read(java.io.InputStream body) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
        } catch (Exception e) {
            throw new CatalogExceptions.CatalogUnavailable("unreadable answer from the catalog", e);
        }
    }

    /** Wrapping ResourceAccessException keeps « connection refused » on the retryable path. */
    static CatalogExceptions.CatalogUnavailable asUnavailable(ResourceAccessException e) {
        return new CatalogExceptions.CatalogUnavailable("catalog not reachable", e);
    }
}

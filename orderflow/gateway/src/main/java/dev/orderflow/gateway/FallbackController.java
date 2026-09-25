package dev.orderflow.gateway;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * What a client gets when a route's circuit breaker is open (Sessions 06 and 07).
 *
 * <p>A clear 503 with {@code Retry-After} beats a thirty-second hang or a stack trace. Note that
 * this breaker and the one inside the order service are not redundant: this one protects
 * <b>clients</b> from a dead route, the other protects the <b>order service</b> from a dead
 * dependency. They fail at different boundaries.</p>
 */
@RestController
public class FallbackController {

    @RequestMapping("/fallback/catalog")
    public ResponseEntity<ProblemDetail> catalogUnavailable() {
        return unavailable("The catalogue is temporarily unavailable");
    }

    @RequestMapping("/fallback/orders")
    public ResponseEntity<ProblemDetail> ordersUnavailable() {
        return unavailable("The order service is temporarily unavailable");
    }

    private ResponseEntity<ProblemDetail> unavailable(String message) {
        ProblemDetail detail = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, message);
        detail.setTitle("Dependency unavailable");
        detail.setProperty("retryable", true);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "5")
                .body(detail);
    }
}

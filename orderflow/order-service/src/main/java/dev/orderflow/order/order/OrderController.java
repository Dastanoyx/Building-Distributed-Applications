package dev.orderflow.order.order;

import dev.orderflow.order.idempotency.IdempotencyService;
import dev.orderflow.order.order.dto.OrderResponse;
import dev.orderflow.order.order.dto.PlaceOrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/**
 * HTTP entry point for orders.
 *
 * <p>{@code POST} is not idempotent by nature, so this endpoint <b>requires</b> an
 * {@code Idempotency-Key} header and implements the claim/replay protocol around the use case
 * (Session 05). Without it, a client that times out and retries creates two orders and reserves
 * the stock twice — the failure the course keeps coming back to.</p>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;
    private final IdempotencyService idempotency;

    public OrderController(OrderService service, IdempotencyService idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    /**
     * POST /api/orders with an {@code Idempotency-Key} header.
     *
     * <ul>
     *   <li>first call → 201 with the created order;</li>
     *   <li>same key, same body, already finished → the stored 201 is replayed;</li>
     *   <li>same key, same body, still running → 409 with {@code Retry-After};</li>
     *   <li>same key, different body → 409 (client bug).</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<?> place(@RequestHeader(value = "Idempotency-Key") @NotBlank String idempotencyKey,
                                   @Valid @RequestBody PlaceOrderRequest request) {

        IdempotencyService.Decision decision = idempotency.claimOrReplay(idempotencyKey, request);

        if (decision instanceof IdempotencyService.Decision.Replay replay) {
            return ResponseEntity.status(replay.status())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(replay.body());
        }
        if (decision instanceof IdempotencyService.Decision.InProgress) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .header(HttpHeaders.RETRY_AFTER, "1")
                    .body("{\"title\":\"Request already in progress\",\"status\":409}");
        }

        try {
            OrderResponse created = service.place(request);
            idempotency.complete(idempotencyKey, HttpStatus.CREATED.value(), created);
            return ResponseEntity.created(URI.create("/api/orders/" + created.id())).body(created);
        } catch (RuntimeException e) {
            // The work failed, so free the key: the client is allowed to try again for real.
            idempotency.abandon(idempotencyKey);
            throw e;
        }
    }

    /** GET /api/orders/{id} — degrades to a non-enriched view when the catalog is down. */
    @GetMapping("/{id}")
    public OrderResponse byId(@PathVariable UUID id) {
        return service.view(id);
    }

    /** GET /api/orders?customerId=...&page=0&size=20 */
    @GetMapping
    public Page<OrderResponse> byCustomer(@RequestParam String customerId,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return service.byCustomer(customerId, PageRequest.of(page, Math.min(size, 100)));
    }
}

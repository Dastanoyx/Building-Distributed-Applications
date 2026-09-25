package dev.orderflow.catalog.reservation;

import dev.orderflow.catalog.reservation.dto.ReservationRequest;
import dev.orderflow.catalog.reservation.dto.ReservationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * The endpoint the order service calls during checkout.
 *
 * <p>{@code Idempotency-Key} is accepted and logged for traceability, but the real protection
 * is the unique constraint on {@code (order_id, sku)} inside the service: a header can be
 * forgotten, a database constraint cannot (Session 05).</p>
 */
@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

    private final ReservationService service;

    public ReservationController(ReservationService service) {
        this.service = service;
    }

    /**
     * POST /api/reservations — holds stock for a whole order.
     *
     * <p>Returns 201 for a fresh reservation and 200 when a duplicate request was replayed,
     * so the caller can tell the two apart if it cares.</p>
     */
    @PostMapping
    public ResponseEntity<ReservationResponse> reserve(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody ReservationRequest request) {

        ReservationResponse response = service.reserve(request);
        HttpStatus status = "HELD".equals(response.status()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }

    /** DELETE /api/reservations/{orderId} — the compensation. Idempotent by design. */
    @DeleteMapping("/{orderId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable UUID orderId) {
        service.release(orderId);
    }
}

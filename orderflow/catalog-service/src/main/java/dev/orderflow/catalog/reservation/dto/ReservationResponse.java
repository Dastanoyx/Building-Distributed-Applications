package dev.orderflow.catalog.reservation.dto;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Result of a reservation.
 *
 * @param status      {@code HELD} for a fresh reservation, {@code ALREADY_HELD} when this was a
 *                    duplicate request that was replayed rather than applied twice
 * @param totalPrice  priced by the catalog, which owns prices — the caller must never compute it
 */
public record ReservationResponse(UUID orderId, String status, BigDecimal totalPrice) {

    public static ReservationResponse held(UUID orderId, BigDecimal total) {
        return new ReservationResponse(orderId, "HELD", total);
    }

    public static ReservationResponse alreadyHeld(UUID orderId, BigDecimal total) {
        return new ReservationResponse(orderId, "ALREADY_HELD", total);
    }
}

package dev.orderflow.catalog.reservation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * « Hold stock for this order. »
 *
 * <p>All lines travel in <b>one</b> request on purpose. Twenty-five separate calls would cost
 * twenty-five network round trips (Session 01) and, worse, could leave fourteen lines reserved
 * and eleven not — a partial failure nobody asked for. One request has one outcome.</p>
 */
public record ReservationRequest(

        @NotNull UUID orderId,

        @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotNull String sku, @Min(1) int quantity) {
    }
}

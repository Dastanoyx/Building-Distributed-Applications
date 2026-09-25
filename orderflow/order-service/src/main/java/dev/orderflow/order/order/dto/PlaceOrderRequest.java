package dev.orderflow.order.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * What a client sends to place an order.
 *
 * <p>Note what is <b>not</b> here: no price, and no customer id in the body when the request is
 * authenticated — the price is owned by the catalog and the identity comes from the token
 * (Session 10). Anything a client could forge should not be trusted from the payload.</p>
 */
public record PlaceOrderRequest(

        @NotBlank String customerId,

        @NotEmpty @Valid List<Line> lines) {

    public record Line(@NotBlank String sku, @Min(1) int quantity) {
    }
}

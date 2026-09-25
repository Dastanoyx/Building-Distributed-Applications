package dev.orderflow.catalog.product.dto;

import dev.orderflow.catalog.product.Product;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * What a client may send when creating a product.
 *
 * <p>A record, not the entity: the API contract and the database schema are allowed to evolve
 * separately, and no lazy-loading proxy can ever reach the JSON serialiser (Session 02).
 * The constraints are the documentation — a reader knows the rules without opening the service.</p>
 */
public record ProductRequest(

        @NotBlank
        @Pattern(regexp = Product.SKU_PATTERN, message = "SKU must look like ABC-123")
        String sku,

        @NotBlank @Size(max = 120)
        String name,

        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 10, fraction = 2)
        BigDecimal price,

        @Min(0)
        int stock) {
}

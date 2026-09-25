package dev.orderflow.catalog.product.dto;

import dev.orderflow.catalog.product.Product;

import java.math.BigDecimal;

/**
 * What the API returns for a product.
 *
 * <p>{@code inStock} is computed here rather than stored: derived values belong to the
 * response, not to the table. Adding a field to this record is a backward-compatible change;
 * renaming one is not (Session 05).</p>
 */
public record ProductResponse(String sku, String name, BigDecimal price, int stock, boolean inStock) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(product.getSku(), product.getName(), product.getPrice(),
                product.getStock(), product.isInStock());
    }
}

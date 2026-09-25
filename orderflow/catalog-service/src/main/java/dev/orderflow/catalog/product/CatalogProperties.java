package dev.orderflow.catalog.product;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration, validated at startup.
 *
 * <p>A wrong value fails the application immediately with a readable message instead of
 * surfacing as odd behaviour hours later. Values come from {@code application.yaml}, and any
 * of them can be overridden by an environment variable in the container — which is how the
 * same image runs in every environment (Sessions 02 and 04).</p>
 *
 * @param maxPageSize hard ceiling on {@code ?size=}; larger requests are clamped, not rejected
 */
@ConfigurationProperties(prefix = "orderflow.catalog")
@Validated
public record CatalogProperties(@Min(1) @Max(200) int maxPageSize) {
}

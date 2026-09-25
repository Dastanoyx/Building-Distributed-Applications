package dev.orderflow.order.client;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * Everything the catalog client needs, typed and validated at startup (Session 05).
 *
 * @param baseUrl        where the catalog lives; a service NAME in Docker, never localhost
 * @param connectTimeout how long to wait for the TCP connection — short: either it is there or it is not
 * @param readTimeout    how long to wait for the answer; derive it from the observed p99, never
 *                       from a round number
 */
@ConfigurationProperties(prefix = "orderflow.catalog")
@Validated
public record CatalogClientProperties(@NotBlank String baseUrl,
                                      @NotNull Duration connectTimeout,
                                      @NotNull Duration readTimeout) {
}

package dev.orderflow.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Catalog service: owns products, prices and stock, and is the only service allowed to
 * touch {@code catalog_db}.
 *
 * <p>Everyone else asks it through HTTP — that boundary is what lets this service change
 * its schema, scale, or be rewritten without coordinating with anybody (Session 03).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }
}

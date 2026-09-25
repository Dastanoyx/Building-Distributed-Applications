package dev.orderflow.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Order service: owns the order lifecycle and drives the saga.
 *
 * <p>It is the busiest service of the system because it is where the interesting distributed
 * problems live: a synchronous call to the catalog, an idempotent entry point, a transactional
 * outbox, and a saga with compensation.</p>
 *
 * <p>{@link EnableScheduling} switches on two background jobs — the outbox drainer and the saga
 * sweeper. Both are written to be safe when several replicas run them (Sessions 09 and 13).</p>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class OrderApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}

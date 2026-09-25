package dev.orderflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The only door into the system (Session 07).
 *
 * <p>It does the work every service would otherwise duplicate: routing, the correlation id,
 * rate limiting, and a circuit breaker per route. It does <b>not</b> do business logic — a
 * gateway that knows your domain has quietly become the new monolith, owned by nobody.</p>
 */
@SpringBootApplication
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}

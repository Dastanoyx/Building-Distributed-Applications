package dev.orderflow.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Payment service: consumes a command, produces a fact.
 *
 * <p>It has <b>no database and no REST API</b>, which makes it the clearest example in the
 * project of a service whose entire contract is a pair of Kafka topics. It is also the service
 * that makes the saga's compensation path real, because it declines anything above a limit.</p>
 */
@SpringBootApplication
public class PaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentApplication.class, args);
    }
}

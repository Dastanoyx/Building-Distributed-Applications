package dev.orderflow.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Notification service: listens to facts and reacts.
 *
 * <p>It never calls anybody and nobody calls it to make it work — it only consumes events. That
 * is why an outage here delays notifications instead of breaking checkout, and why a new
 * consumer can be added to the system without touching the producer (Session 08).</p>
 */
@SpringBootApplication
public class NotificationApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationApplication.class, args);
    }
}

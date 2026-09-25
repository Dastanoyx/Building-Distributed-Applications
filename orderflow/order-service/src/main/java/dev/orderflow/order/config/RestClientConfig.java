package dev.orderflow.order.config;

import dev.orderflow.order.client.CatalogClientProperties;
import dev.orderflow.order.shared.CorrelationIdFilter;
import org.slf4j.MDC;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client used to call the catalog (Session 05).
 *
 * <p>Two things are configured here and nowhere else, so they cannot be forgotten at a call
 * site: the <b>timeouts</b>, and the propagation of the <b>correlation id</b>. A call without a
 * timeout is the single most common cause of a cascading failure — threads and connections pile
 * up waiting for a service that will never answer (Session 06).</p>
 */
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient catalogRestClient(RestClient.Builder builder, CatalogClientProperties properties) {
        ClientHttpRequestFactorySettings settings = ClientHttpRequestFactorySettings.DEFAULTS
                .withConnectTimeout(properties.connectTimeout())
                .withReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(ClientHttpRequestFactories.get(settings))
                .defaultHeader("X-Service", "order-service")
                // Carry the correlation id downstream so one grep shows the whole request.
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
                    if (correlationId != null) {
                        request.getHeaders().add(CorrelationIdFilter.HEADER, correlationId);
                    }
                    return execution.execute(request, body);
                })
                .build();
    }
}

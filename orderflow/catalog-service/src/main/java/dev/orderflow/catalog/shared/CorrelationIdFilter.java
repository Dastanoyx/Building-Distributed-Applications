package dev.orderflow.catalog.shared;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;
import java.util.UUID;

/**
 * Puts the caller's correlation id into the logging context so every log line of this
 * request carries it (Session 07 and 11).
 *
 * <p>The id is created by the gateway and forwarded by every service. One {@code grep} then
 * returns the complete story of one request across four services, which is the difference
 * between a five-minute investigation and an afternoon of matching timestamps.</p>
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String correlationId = Optional.ofNullable(request.getHeader(HEADER))
                .filter(value -> !value.isBlank())
                .orElseGet(() -> UUID.randomUUID().toString());

        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            // Mandatory: servlet containers reuse threads, so a leaked value would attach
            // this id to somebody else's request.
            MDC.remove(MDC_KEY);
        }
    }
}

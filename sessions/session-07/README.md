# Session 07 — Gateway, discovery and configuration

**Goal of the lab:** Put one door in front of the system and make every log line traceable.

## Code to read

| File | Why |
|---|---|
| `orderflow/gateway/src/main/resources/application.yaml` | routes = predicates + filters, per-route breaker and timeout |
| `orderflow/gateway/.../CorrelationIdFilter.java` | the id that joins four services into one story |
| `orderflow/gateway/.../FallbackController.java` | what a client sees when a route is open |
| `orderflow/catalog-service/.../shared/CorrelationIdFilter.java` | the receiving half: MDC in, MDC out in a finally block |

## What you build

Add the gateway, route everything through it, and make one request produce log lines with the same id in gateway, order and catalog.

## Commands

```bash
curl -s -D- -o /dev/null localhost:8080/api/products | grep -i correlation
docker compose logs --no-log-prefix | grep <the-id-you-just-got>
```

## It works when

- clients only need port 8080
- a stopped service produces the gateway's 503 fallback, not a stack trace
- one correlation id retrieves the whole request across services

---

Statements and solutions: `Session_07_Exercises.pdf` and `Session_07_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

# Session 06 — Resilience: retry, circuit breaker, bulkhead, degraded mode

**Goal of the lab:** Survive a dependency that is slow, broken, or gone — visibly and on purpose.

## Code to read

| File | Why |
|---|---|
| `orderflow/order-service/src/main/resources/application.yaml` | the `resilience4j:` block is the policy, readable by anyone |
| `orderflow/order-service/.../client/CatalogClient.java` | `@Retry` + `@CircuitBreaker` and the fallbacks that re-throw business answers |
| `orderflow/order-service/.../shared/ApiExceptionHandler.java` | 503 + Retry-After for an outage, never a fake reservation |

## What you build

Add the retry, the breaker and the bulkhead; then write `docs/degradation.md` listing, per dependency, what still works and what the user sees.

## Commands

```bash
docker compose up -d
docker compose stop catalog-service
curl -i -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -H "Idempotency-Key: $(uuidgen)" -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":1}]}'
curl -s localhost:8080/api/orders/<an-existing-id> | jq '.enriched'
docker compose start catalog-service
```

## It works when

- repeated failures open the breaker and the next call fails in under 50 ms
- a 409 from the catalog never opens the breaker
- reads degrade (`enriched:false`), writes return 503 + Retry-After

> **Project checkpoint 1** — two services, own databases, one-command startup, and a killed dependency that degrades instead of hanging.

---

Statements and solutions: `Session_06_Exercises.pdf` and `Session_06_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

# Session 05 — Service-to-service calls, timeouts and idempotency

**Goal of the lab:** Create the order service and let it call the catalog safely.

## Code to read

| File | Why |
|---|---|
| `orderflow/order-service/.../config/RestClientConfig.java` | timeouts and correlation-id propagation configured once |
| `orderflow/order-service/.../client/CatalogClient.java` | status translation: 404 → caller error, 409 → business, 5xx → outage |
| `orderflow/order-service/.../idempotency/` | claim-then-complete, with the key as PRIMARY KEY |
| `orderflow/order-service/src/test/.../PlaceOrderIT.java` | WireMock scripts the catalog, including the outage |

## What you build

Implement the client and the idempotency table; prove with a test that the same key twice creates one order and calls the catalog once.

## Commands

```bash
mvn -pl order-service -am verify
K=$(uuidgen); for i in 1 2; do curl -s -o /dev/null -w "%{http_code}\n" -X POST localhost:8080/api/orders -H 'Content-Type: application/json' -H "Idempotency-Key: $K" -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":1}]}'; done
```

## It works when

- a slow catalog fails near the read timeout instead of hanging
- the same key twice → one order
- a different body with the same key → 409

---

Statements and solutions: `Session_05_Exercises.pdf` and `Session_05_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

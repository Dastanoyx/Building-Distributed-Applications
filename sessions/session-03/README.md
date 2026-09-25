# Session 03 — PostgreSQL, JPA, Flyway and Testcontainers

**Goal of the lab:** Give the catalog a real schema, and make stock reservation impossible to race.

## Code to read

| File | Why |
|---|---|
| `orderflow/catalog-service/src/main/resources/db/migration/` | V1, V2, V3 — forward-only, with CHECK constraints |
| `orderflow/catalog-service/.../product/ProductRepository.java` | the conditional UPDATE `... WHERE stock >= :quantity` |
| `orderflow/catalog-service/.../reservation/ReservationService.java` | one transaction, all-or-nothing, idempotent |
| `orderflow/catalog-service/src/test/.../ReservationServiceIT.java` | real PostgreSQL, and the ten-thread overselling test |

## What you build

Write the migrations, then implement reservation the naive way (read, check, save), watch the concurrency test fail, then fix it with the conditional update.

## Commands

```bash
mvn -pl catalog-service -am verify
docker compose exec catalog-db psql -U catalog -d catalog -c 'select sku, stock from product;'
```

## It works when

- `concurrent_reservations_never_oversell` passes
- `ddl-auto=validate` makes the app refuse to start when an entity and a migration disagree
- the container starts once per test class, not per method

---

Statements and solutions: `Session_03_Exercises.pdf` and `Session_03_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

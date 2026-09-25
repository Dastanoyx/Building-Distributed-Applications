# Session 12 — Scaling, caching and performance

**Goal of the lab:** Find the real bottleneck instead of guessing.

## Code to read

| File | Why |
|---|---|
| `orderflow/load/place-order.js` | the k6 scenario, with thresholds that can fail a pipeline |
| `orderflow/order-service/src/main/resources/application.yaml` | the Hikari block: pool size is PER INSTANCE |
| `orderflow/order-service/.../order/OrderRepository.java` | `@EntityGraph` against the N+1 |
| `orderflow/catalog-service/src/main/resources/db/migration/V1__create_product.sql` | the index that changes everything |

## What you build

Measure a baseline, scale out, explain why throughput did not triple, then fix the real limit (index, N+1, pool size) and measure again.

## Commands

```bash
docker run --rm -i --network host -v "$PWD/load:/load" grafana/k6 run /load/place-order.js
docker compose up -d --scale order-service=3
docker compose exec order-db psql -U orders -d orders -c 'select count(*) from pg_stat_activity;'
```

## It works when

- you can state your p50/p95/p99 and throughput
- you can name your bottleneck and prove it with a metric
- virtual threads changed the numbers only where the thread count was the limit

---

Statements and solutions: `Session_12_Exercises.pdf` and `Session_12_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

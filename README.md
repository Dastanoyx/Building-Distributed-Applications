# OrderFlow — the course code

Working source code for **Building Distributed Applications** (Java 21 · Spring Boot 3 ·
PostgreSQL · Docker · Kafka). Course by **Yassin Ghariani**.

Two things live here:

| Folder | What it is |
|---|---|
| `orderflow/` | The capstone system: five services, three databases, Kafka, observability, Kubernetes manifests, load test. Every class is documented and names the session it comes from. |
| `sessions/` | One guide per session: what to build, which files to read, what to run, and the exercise that goes with it. |

Start with **[GETTING_STARTED.md](GETTING_STARTED.md)** — install to first order in about
fifteen minutes.

---

## The system in one picture

```
                          ┌─────────────┐
            clients ─────▶│   gateway   │ :8080   routing, correlation id, breaker
                          └──────┬──────┘
            ┌────────────────────┼─────────────────────┐
            ▼                    ▼                     ▼
     ┌────────────┐      ┌────────────┐        ┌──────────────┐
     │  catalog   │:8081 │   order    │:8082   │ notification │:8083
     │  service   │◀────▶│  service   │        │   service    │
     └─────┬──────┘ REST └─────┬──────┘        └──────┬───────┘
           │                   │                      │
      ┌────▼────┐         ┌────▼────┐            ┌────▼────┐
      │catalog  │         │ order   │            │notif.   │
      │  _db    │         │  _db    │            │  _db    │
      └─────────┘         └─────────┘            └─────────┘
                               │                      ▲
                               ▼                      │
                          ┌─────────┐   order-events  │
                          │  Kafka  │─────────────────┘
                          └────┬────┘
                               │ payment-commands / payment-events
                          ┌────▼────────┐
                          │   payment   │:8084  (no database, events only)
                          └─────────────┘

   observability: Prometheus :9090 · Grafana :3000 · Jaeger :16686
```

## What each service demonstrates

| Service | Read it for |
|---|---|
| `catalog-service` | Layering and DTOs, Flyway, the conditional UPDATE that cannot oversell, Testcontainers |
| `order-service` | HTTP client with timeouts, retry and circuit breaker, idempotency keys, the transactional outbox, the saga |
| `payment-service` | A service whose entire contract is two Kafka topics, and an idempotent consumer |
| `notification-service` | At-least-once delivery, deduplication, retries and a dead letter topic |
| `gateway` | One entry point, correlation ids, per-route breakers and fallbacks |
| `common-events` | The only shared code: the event contracts |

## Conventions used everywhere

- **No service reads another service's database.** Data is shared through an API or an event.
- **Every outbound call has a timeout.** A call without one is how a slow dependency takes a service down.
- **Every write that can be retried is idempotent.** Keys, unique constraints, conditional updates.
- **Flyway owns the schema**; Hibernate is set to `validate` and never creates a table.
- **Configuration comes from the environment**, so the same image runs everywhere. Secrets live in `.env`, which is git-ignored.
- **Logs go to stdout** as one line per event, carrying the correlation id.

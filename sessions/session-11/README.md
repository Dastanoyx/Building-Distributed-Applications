# Session 11 — Observability: metrics, logs and traces

**Goal of the lab:** Be able to answer « why was that order slow? » in under a minute.

## Code to read

| File | Why |
|---|---|
| `orderflow/ops/prometheus.yml` | Prometheus pulls; the services only expose |
| `orderflow/ops/grafana/provisioning/dashboards/orderflow.json` | the four golden signals, provisioned as code |
| `orderflow/order-service/.../config/MetricsConfig.java` | business gauges, and why no order id is ever a tag |
| `orderflow/order-service/.../saga/OrderSagaOrchestrator.java` | one gauge per saga state |

## What you build

Add tracing with the OTLP exporter, switch logging to JSON with the correlation id, and build the dashboard.

## Commands

```bash
open http://localhost:3000
open http://localhost:16686
curl -s localhost:8082/actuator/prometheus | grep orderflow_
```

## It works when

- one trace shows gateway → order → catalog → JDBC → Kafka → consumer
- `orderflow_outbox_pending` climbs when Kafka is down
- every log line carries a correlation id

> **Project checkpoint 2** — events, outbox, saga, resilience and security working, and an incident you can diagnose from your own dashboard.

---

Statements and solutions: `Session_11_Exercises.pdf` and `Session_11_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

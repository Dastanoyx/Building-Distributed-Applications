# Session 01 — Foundations, toolchain and the target architecture

**Goal of the lab:** Get the whole stack running on your machine and understand what you are looking at.

## Code to read

| File | Why |
|---|---|
| `orderflow/docker-compose.yml` | every service, its database, Kafka and the observability stack in one file |
| `orderflow/pom.xml` | the multi-module layout: five services plus the shared events module |
| `orderflow/common-events/src/main/java/dev/orderflow/events/OrderEvent.java` | the contracts that cross service boundaries — read the class comment first |

## What you build

Nothing yet — today you verify the toolchain and read the architecture.

## Commands

```bash
docker compose up -d catalog-db
docker compose exec catalog-db psql -U catalog -d catalog -c 'select version();'
docker compose down
```

## It works when

- `java -version` says 21, `docker compose version` says v2
- you can start and stop a PostgreSQL container and explain why its data disappeared without a volume

---

Statements and solutions: `Session_01_Exercises.pdf` and `Session_01_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

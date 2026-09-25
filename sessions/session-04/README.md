# Session 04 — Containers, images and Compose

**Goal of the lab:** Package each service into a small, cached, non-root image and start the stack with one command.

## Code to read

| File | Why |
|---|---|
| `orderflow/catalog-service/Dockerfile` | multi-stage, dependencies before sources, HEALTHCHECK, exec-form entrypoint |
| `orderflow/.dockerignore` | why the build context must stay small |
| `orderflow/docker-compose.yml` | named volumes, health-gated `depends_on`, `${VAR:?}` for secrets |
| `orderflow/catalog-service/src/main/resources/application.yaml` | graceful shutdown and the actuator probes |

## What you build

Write the Dockerfile yourself, first as one stage, then multi-stage; measure both image sizes and both rebuild times after a one-line code change.

## Commands

```bash
docker compose up -d --build
docker image ls | grep orderflow
docker compose exec catalog-service id
docker compose down -v
```

## It works when

- the image is around 230 MB, not 780 MB
- `docker compose exec catalog-service id` does not say root
- data survives `docker compose down` and disappears after `down -v`

---

Statements and solutions: `Session_04_Exercises.pdf` and `Session_04_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

# Getting started — from an empty machine to a placed order

About fifteen minutes, most of it downloads. Everything runs locally in containers; no cloud
account, no paid service.

---

## 1. Install the prerequisites

| Tool | Version | Check with | Where |
|---|---|---|---|
| Java (JDK) | **21** | `java -version` | [adoptium.net](https://adoptium.net) (Temurin 21) |
| Maven | 3.9+ | `mvn -v` | [maven.apache.org](https://maven.apache.org/download.cgi) — or use the `mvnw` wrapper |
| Docker Engine | 24+ | `docker --version` | Docker Desktop (Windows/macOS) or `docker.io` (Linux) |
| Docker Compose | **v2** | `docker compose version` | Ships with Docker Desktop; on Linux: `docker-compose-plugin` |
| Git | any recent | `git --version` | [git-scm.com](https://git-scm.com) |
| curl + jq | any | `curl --version`, `jq --version` | Usually preinstalled; `jq` is optional but makes output readable |

**Machine:** 8 GB RAM minimum (the full stack runs about 3.5 GB), 10 GB free disk.

<details>
<summary>Ubuntu / Debian, one block</summary>

```bash
sudo apt-get update
sudo apt-get install -y openjdk-21-jdk maven git curl jq
# Docker, official repository
curl -fsSL https://get.docker.com | sudo sh
sudo usermod -aG docker "$USER"   # log out and back in for this to take effect
```
</details>

<details>
<summary>macOS with Homebrew</summary>

```bash
brew install --cask temurin@21 docker
brew install maven git jq
open -a Docker        # start Docker Desktop once so the daemon is running
```
</details>

<details>
<summary>Windows</summary>

Install **Docker Desktop with the WSL 2 backend**, then work inside your WSL distribution and
follow the Ubuntu instructions there. Building Java inside WSL and running Docker Desktop on
Windows is the combination that gives the fewest surprises.
</details>

Verify everything at once:

```bash
java -version && mvn -v && docker --version && docker compose version
```

---

## 2. Get the code and set the secrets

```bash
git clone <your-repository-url> orderflow-course
cd orderflow-course/orderflow

cp .env.example .env      # passwords for the local databases; .env is git-ignored
```

`.env` is the only place credentials live. Compose refuses to start if a value is missing,
which is deliberate: an empty database password should fail loudly, not silently.

---

## 3. Start everything

```bash
docker compose up -d --build
```

First run takes 3–6 minutes (Maven downloads, images pulled). Afterwards it is about 30 seconds.

Watch it come up:

```bash
docker compose ps           # all services should reach "healthy" or "running"
docker compose logs -f order-service
```

| What | Address |
|---|---|
| Gateway (use this one) | http://localhost:8080 |
| Catalog / Order / Notification / Payment | :8081 / :8082 / :8083 / :8084 |
| Prometheus | http://localhost:9090 |
| Grafana (`admin` / value of `GRAFANA_PASSWORD`) | http://localhost:3000 |
| Jaeger (traces) | http://localhost:16686 |

---

## 4. Place your first order

```bash
# The catalogue is seeded by a Flyway migration
curl -s localhost:8080/api/products | jq '.content[] | {sku, name, price, stock}'

# Place an order. POST needs an Idempotency-Key: retrying without one creates a second order.
curl -s -X POST localhost:8080/api/orders \
  -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"customerId":"c-1","lines":[{"sku":"KEY-001","quantity":2}]}' | jq

# A couple of seconds later, the notification produced by the event has arrived
curl -s "localhost:8080/api/notifications?customerId=c-1" | jq '.content[] | {type, message}'
```

Then run the guided demo, which also breaks things on purpose:

```bash
./scripts/demo.sh
```

---

## 5. Run it without Docker (for debugging in your IDE)

Start only the infrastructure, run one service from your IDE:

```bash
docker compose up -d catalog-db order-db notification-db kafka
mvn -pl catalog-service -am spring-boot:run       # port 8081
mvn -pl order-service   -am spring-boot:run       # port 8082
```

The default values in each `application.yaml` already point at `localhost`, so nothing to
configure. Breakpoints work normally.

---

## 6. Run the tests

```bash
mvn test                       # unit tests only, a few seconds
mvn verify                     # adds the integration tests (Testcontainers needs Docker running)
mvn -pl catalog-service test   # one module
```

Integration tests start their own PostgreSQL container — no shared test database, nothing to
clean up.

---

## 7. Useful commands

```bash
docker compose logs -f --tail=100 order-service        # follow one service
docker compose exec catalog-db psql -U catalog -d catalog -c '\dt'
docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:9092 --topic order-events --from-beginning \
  --property print.key=true                            # watch the events flow

docker compose restart order-service
docker compose down                                    # stop, keep the data
docker compose down -v                                 # stop and delete the databases
```

---

## Troubleshooting

| Symptom | Cause and fix |
|---|---|
| `port is already allocated` | Something else uses 8080/5432. Stop it, or change the published port in `docker-compose.yml`. |
| Service exits, log says `Connection to ... refused` | It started before its database. Compose waits for health, so this usually means the database container is unhealthy: `docker compose logs catalog-db`. |
| `CATALOG_DB_PASSWORD: variable is not set` | You skipped `cp .env.example .env`. |
| Container restarts, exit code **137** | Out of memory. Give Docker Desktop more RAM, or lower the service's memory limit — never set `-Xmx` equal to the container limit (Session 04). |
| Tests fail with `Could not find a valid Docker environment` | Testcontainers needs the Docker daemon running and your user in the `docker` group. |
| `mvn` downloads forever on the first build | Normal once. The Docker build caches it in a layer afterwards. |
| Kafka listener logs endless `Revoking / Assigned` | A consumer is too slow and exceeds `max.poll.interval.ms`. Shorten the work inside the listener (Session 08). |
| Everything is slow after hours of use | `docker system prune` reclaims stopped containers and dangling images. |

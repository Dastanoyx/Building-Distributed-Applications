# Session 14 — CI/CD, Kubernetes and safe releases

**Goal of the lab:** Ship the same artefact everywhere, and change the schema without downtime.

## Code to read

| File | Why |
|---|---|
| `orderflow/k8s/catalog-service/deployment.yaml` | three probes, `maxUnavailable: 0`, resource limits |
| `orderflow/k8s/catalog-service/service.yaml` | DNS-based discovery in a cluster |
| `orderflow/k8s/catalog-service/hpa.yaml` | autoscaling, which only works because the service is stateless |
| `orderflow/catalog-service/src/main/resources/db/migration/` | expand → migrate → contract, one release each |

## What you build

Write the GitHub Actions pipeline (test → scan → build → push), deploy the catalog to kind, and perform a rename in four backward-compatible releases.

## Commands

```bash
kind create cluster --name orderflow
docker build -f catalog-service/Dockerfile -t orderflow/catalog:dev .
kind load docker-image orderflow/catalog:dev --name orderflow
kubectl apply -f k8s/catalog-service/
kubectl get pods -w
kubectl rollout undo deploy/catalog-service
```

## It works when

- a broken build stalls the rollout with zero failed requests
- liveness does not depend on the database — prove what happens if it does
- the old jar still runs against the schema after each migration step

---

Statements and solutions: `Session_14_Exercises.pdf` and `Session_14_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

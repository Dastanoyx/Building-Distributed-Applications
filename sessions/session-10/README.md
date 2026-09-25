# Session 10 — Security: tokens, authorisation and secrets

**Goal of the lab:** Stop trusting the network.

## Code to read

| File | Why |
|---|---|
| `orderflow/.env.example` | the only place credentials live; `.env` is git-ignored |
| `orderflow/docker-compose.yml` | `${VAR:?}` so a missing secret fails loudly |
| `orderflow/k8s/catalog-service/config.yaml` | ConfigMap vs Secret, and why a Secret is not encryption |

## What you build

Add Keycloak, turn the services into OAuth2 resource servers, validate the audience, and enforce ownership: a customer may only read their own orders.

## Commands

```bash
git log -p | grep -iE 'password|api[_-]key' || echo 'no secret in the history'
docker compose config | grep -i password | head
```

## It works when

- a request with no token gets 401 and a token for another audience gets 401
- customer A cannot read customer B's order
- no secret anywhere in the Git history

---

Statements and solutions: `Session_10_Exercises.pdf` and `Session_10_Solutions.pdf` in the course kit. Setup instructions: [../GETTING_STARTED.md](../GETTING_STARTED.md).

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues, developed as the team project for TUM's DevOps course (CIT423001, summer term 2026). It is a graded, mono-repo deliverable: the *engineering process* (CI/CD, observability, reproducible deploys) is graded as heavily as the application itself. See `docs/task-description.md` for the binding course requirements, `docs/problem-statement.md` for the domain framing, and `docs/architecture.md` for the system design.

**Subsystem ownership** (matters for who reviews what and who is examined on what):
- **Frontend (`client/`)** — Arthur Gersbacher
- **Backend Spring services (`services/`)** — Johannes Kirchner
- **GenAI service (`services/genai-service/`)** — Luca Kollmer

Cross-subsystem work on CI/CD, Kubernetes, and observability is expected and tracked through PRs.

## Repository Layout

All services and the shared infra now exist. The structure below reflects the repo as it stands.

```
client/                            — React 19 + Vite 8 + TS frontend
services/
  gateway-service/                 — Spring Cloud Gateway, OAuth2 resource server, single public ingress (port 8080)
  auth-service/                    — Spring Boot 4.0.6, Java 21; OAuth2 Authorization Server + Resource Server (8081)
  lost-item-service/               — Spring Boot 4.0.6 (8082)
  found-item-service/              — Spring Boot 4.0.6 (8083)
  matching-service/                — Spring Boot 4.0.6, owns the pgvector index (8084)
  notification-service/            — Spring Boot 4.0.6 (8085)
  operations-service/              — Spring Boot 4.0.6, owns venues + staff profiles (8086)
  genai-service/                   — Python 3.12 + FastAPI (8000)
api/openapi.yaml                   — OpenAPI 3.1 spec — currently GenAI service only (Spring paths arrive with #61)
client/openapi/                    — cached per-service OpenAPI snapshots (auth.json, lost-items.json, found-items.json) feeding Orval
infra/
  prometheus/                      — prometheus.yml, alerts.yml, alerts_test.yml (promtool-tested in CI)
  grafana/                         — provisioning/ + dashboards/ (JSON, mounted by both compose and Helm)
  helm/foundflow/                  — chart for AET + local k8s deploys; see infra/helm/foundflow/README.md
tests/e2e/foundflow-e2e.ps1        — PowerShell E2E suite driven by CI against docker-compose
docs/                              — architecture, problem statement, task brief, local-k8s, api-security, photo-storage
docker-compose.yml                 — full local stack
```

## Common Commands

### Frontend (`client/`)
```
npm install
npm run dev            # Vite dev server
npm run build          # tsc -b && vite build
npm run lint           # eslint .
npm run preview        # serve built bundle
npm run codegen        # regenerate Orval client from cached snapshots in client/openapi/
npm run codegen:fetch  # pull fresh specs from running gateway, then regenerate
```
Stack: React 19, Vite 8, TypeScript 6, Node 22 (pinned in CI), ESLint 10. No test runner is wired up yet — when adding one, prefer Vitest (it composes with Vite). CI runs `npm run build` and a `codegen-check` job that fails on drift between `client/openapi/` snapshots and the committed generated client under `client/src/api/`.

**Always use the orval-generated models, requests, and zod schemas** under `client/src/api/**` — never hand-write a type, request DTO, or validation schema that the generated client already provides. For form validation, resolve against the generated `*Body` zod schema (e.g. `loginBody` from `@/api/auth/zod`) and type the form with the generated request interface (e.g. `LoginRequest` from `@/api/auth/model`). If custom UI error copy is needed, extend the generated schema rather than redefining the field contract. Hand-written types are only acceptable for shapes with no OpenAPI representation (e.g. decoded-JWT claims).

### Spring Boot services (`services/<name>/`)
The auth-service is the reference scaffold. All seven Spring services (`gateway-service` + the six backend services) share the same Gradle setup: Spring Boot 4.0.6, Java 21 toolchain, JUnit 5.
```
./gradlew bootRun                          # run the service
./gradlew build                            # compile + test + assemble
./gradlew check                            # what CI runs per service (matrix)
./gradlew test --tests '*HelloController*' # single test class/method (Gradle pattern)
```
Each service has a non-conflicting port pinned in `application.properties` / `application.yml` (see Repository Layout above). Most services use Spring Web MVC; `gateway-service` uses Spring Cloud Gateway (WebFlux).

### GenAI service (`services/genai-service/`)
Python 3.12 + FastAPI. Code lives under `app/` (entry `app/main.py`, config in `app/config.py`, providers in `app/providers/{openai,ollama,fake}.py`, schemas in `app/api/schemas.py`).
```
pip install -e '.[dev]'   # install with dev extras (pytest, ruff, respx, httpx)
ruff check                 # lint — runs in CI
pytest -v                  # unit + contract tests — runs in CI
uvicorn app.main:app --reload --port 8000
```
Endpoints: `POST /extract-attributes`, `POST /embed`, `POST /verify-match`, `GET /health`, `GET /_diagnostic`, `GET /metrics` (Prometheus exposition via `prometheus-fastapi-instrumentator`).

### Docker Compose (root)
`docker compose up` brings up the whole stack: all seven Spring services, the genai-service, six dedicated Postgres 17 instances, Ollama + a one-shot `ollama-init` sidecar that pre-pulls the configured chat/vision/embed models, Prometheus 2.55.1, Grafana 11.3.0, and the React client behind nginx.

**Host-exposed ports:**
| Service          | Host  | Container |
|------------------|-------|-----------|
| gateway-service  | 8080  | 8080      |
| genai-service    | 8000  | 8000      |
| client (nginx)   | 3000  | 80        |
| prometheus       | 9090  | 9090      |
| grafana          | 3030  | 3000      |

All Spring backend services (8081–8086) and the six Postgres DBs are internal-only; reach them through the gateway. `.env.example` documents the required env vars; copy to `.env` before first run.

## Gateway Routing

`gateway-service` is the only public ingress. Routes live in `services/gateway-service/src/main/resources/application.yml`.

| Path prefix                            | Upstream                |
|----------------------------------------|-------------------------|
| `/api/auth/**`, `/api/users/**`        | auth-service:8081       |
| `/api/lost-items/**`, `/api/lost-reports/**` | lost-item-service:8082  |
| `/api/found-items/**`                  | found-item-service:8083 |
| `/api/matches/**`                      | matching-service:8084   |
| `/api/notifications/**`                | notification-service:8085 |
| `/api/venues/**`                       | operations-service:8086 |

Per-service actuator endpoints are exposed under `/{slug}/actuator/{health,prometheus,info}` and `/{slug}/v3/api-docs` (rewritten downstream by the gateway). Slugs: `auth`, `lost-items`, `found-items`, `matches`, `notifications`, `venues`. The gateway aggregates Swagger UI via `springdoc.swagger-ui.urls`.

**Service names matter.** Kubernetes Service objects and docker-compose service keys are identical (`auth-service`, `lost-item-service`, …) so the gateway's hardcoded upstream URIs work in compose, local k8s, and AET without per-env config.

## Architecture (the parts that span files)

The system is designed as **event-driven with synchronous edges**, but the event bus is not yet wired:

- **REST/JSON over HTTP** is the dominant pattern today: user-facing commands/queries through the gateway, plus synchronous calls to `genai-service` for attribute extraction, embedding, and message generation.
- **RabbitMQ domain events** (`LostReportCreated`, `FoundItemLogged`, `MatchCandidateCreated`, `MatchConfirmed`, `NotificationSent`, `CaseClosed`) are the planned async pattern per `docs/architecture.md`. RabbitMQ is **not in docker-compose yet** — when it lands, services must use events for intake → matching → notification → operations projections rather than synchronous chains.

**Database isolation is strict.** Each Spring service owns its own PostgreSQL 17 database with its own DB user; services never read each other's tables. Cross-service detail reads go through REST. The `matching-service` database adds the `pgvector` extension and stores embeddings produced by `genai-service`. Migrations are managed per-service with Flyway.

**OpenAPI 3.1 is the contract for sync APIs.** `api/openapi.yaml` currently covers only the GenAI service (per issue #48). Spring-service paths will be added when #61 lands. Until then, the Orval-driven frontend client reads cached per-service snapshots from `client/openapi/*.json` (refreshed via `npm run codegen:fetch` against a running gateway). The `codegen-check` CI job guards against drift. Do not hand-write DTOs that the spec covers, and do not introduce direct cross-service HTTP calls without the generated client.

**Auth.** `auth-service` is a Spring OAuth2 Authorization Server (issues JWTs) and Resource Server. `gateway-service` is a Resource Server that validates JWTs against the auth-service JWK set (`/oauth2/jwks`). `auth-service` owns credentials, sessions, and refresh-token state; `operations-service` owns staff/ops/admin user *profiles*. They are linked by `authSubject` — keep this boundary; don't put profile fields in `auth-service` or credential state in `operations-service`.

**Provider switch for the LLM.** `GENAI_PROVIDER=openai|local` toggles between OpenAI API and a local Ollama backend. Implementations live in `app/providers/{openai,ollama,fake}.py` and share the same provider interface — same code path, no separate implementations. A nightly `genai-integration.yml` workflow runs the service against a real Ollama backend (`qwen2.5:0.5b` + `nomic-embed-text`) and is non-blocking (`continue-on-error: true`).

**Object storage abstraction.** MinIO locally, Azure Blob in cloud. Define and reuse a single photo-storage interface shared by `lost-item-service` and `found-item-service` before implementations land — called out as a known risk in `docs/architecture.md` and detailed in `docs/photo-storage.md`. Not yet implemented.

## CI Workflows

`.github/workflows/ci.yml` runs on push/PR to `development` and `main`. Jobs:

1. **backend-tests** — matrix over the six backend Spring services, runs `./gradlew check`. (Gateway is built but not in the matrix.)
2. **genai-tests** — `ruff check` + `pytest -v` on Python 3.12 with pip cache keyed on `pyproject.toml`.
3. **prometheus-rules-tests** — `promtool test rules` against `infra/prometheus/alerts_test.yml` via the `prom/prometheus:v2.55.1` image.
4. **client-build** — `npm ci && npm run build` on Node 22.
5. **codegen-check** — regenerates the Orval client from `client/openapi/` and fails on `git diff --exit-code client/src/api`.
6. **e2e-tests** — depends on `backend-tests` and `client-build`. Boots the backend stack via `docker compose up --build -d`, polls health endpoints through the gateway, then runs `./tests/e2e/foundflow-e2e.ps1` (PowerShell). Tears down with `docker compose down -v`.

`.github/workflows/genai-integration.yml` — nightly + on-push-to-main, hits real Ollama, non-blocking.

CD to Kubernetes is **not yet wired**. The Helm chart (`infra/helm/foundflow/`) is the deploy artifact; values-aet.yaml is the production override layer. Wiring a CD job to call `helm upgrade --install` on merges to `main` is an open ticket.

## Engineering Constraints from the Course Brief

These are graded requirements, not preferences. Violating them costs points:

- **Three-or-fewer-commands local startup.** `docker compose up` must run the full system end-to-end with sane defaults. No long ENV setup.
- **No hardcoded credentials anywhere.** Local: gitignored `.env` files (`.env.example` documents required vars). Kubernetes: `ConfigMap` for non-secret config, `Secret` for credentials populated from GitHub repository secrets via CI.
- **Every component has its own Dockerfile.** Postgres uses the official image; the compose file declares it.
- **Mandatory PRs.** No direct commits to `main`. Each feature/bugfix lives on its own branch, CI must pass, peer review is required, branches stay short-lived (target ≤2 days).
- **CI must build and test all services on every PR.** CD must auto-deploy to Kubernetes on merge to `main`. CI exists (ci.yml); CD is still pending.
- **Deployable to both Rancher (course infra) and Azure** via Helm. The `infra/helm/foundflow/` chart targets AET (Rancher RKE2) via `values-aet.yaml` and local Kubernetes via `values-local.yaml`.
- **Observability must be meaningful.** Prometheus tracks request count, latency, and error rate at minimum, plus domain metrics (matches/min, GenAI extraction latency, vector search latency). Grafana dashboards committed as JSON under `infra/grafana/dashboards/` (shared by compose and Helm). Alert rules in `infra/prometheus/alerts.yml` are promtool-tested in CI; the Helm chart re-renders them as a `PrometheusRule` CR.
- **Spring services expose `/actuator/prometheus`** via Micrometer; the Python service exposes `/metrics`.
- **OpenAPI/Swagger UI** is exposed by each Spring service (`/v3/api-docs`, `/swagger-ui.html`) and aggregated by the gateway.

## Git Workflow

- Feature, chore, and fix branches are cut from `development` and merged back into `development` via PR. Default `gh pr create --base development`.
- `main` is the release branch. Merging `development → main` is what triggers CD (when CD lands) — only do it when releasing, never as the PR base for ordinary work.
- Start work with `git fetch origin && git checkout -b <branch> origin/development`.
- CI runs on both branches; protect `main` with required-checks once CD is live.

## When Adding a New Spring Service

1. Mirror `services/auth-service/` (Gradle wrapper, Spring Boot 4.0.6, Java 21, JUnit 5).
2. Pick a non-conflicting port in `application.properties` (current allocations: 8080–8086).
3. Wire up a dedicated PostgreSQL 17 database in `docker-compose.yml` *and* `infra/helm/foundflow/values.yaml` (`databases:` + `services.<svc>.dbRef`) — never share with another service.
4. Add the service to the CI matrix in `.github/workflows/ci.yml` (`backend-tests` job).
5. Add a gateway route in `services/gateway-service/src/main/resources/application.yml` covering API paths + the three actuator paths + `/v3/api-docs`. Register the docs URL under `springdoc.swagger-ui.urls`.
6. Expose Actuator + Micrometer Prometheus endpoint; the Helm chart's `ServiceMonitor` template will pick it up automatically when `services.<svc>.metrics: true` (default for `kind: spring`).
7. Generate controller stubs from `api/openapi.yaml` once Spring paths land there (#61). Until then, define the contract per-service and snapshot it into `client/openapi/<svc>.json` for Orval.
8. Emit/consume domain events through RabbitMQ (once the broker lands) rather than chaining synchronous calls when the work is asynchronous (intake, matching, notifications, audit projections).

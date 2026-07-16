# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues, developed as the team project for TUM's DevOps course (CIT423001, summer term 2026). It is a graded, mono-repo deliverable: the *engineering process* (CI/CD, observability, reproducible deploys) is graded as heavily as the application itself. See `docs/course/requirements.md` for the binding course requirements, `docs/product/problem-statement.md` for the domain framing, and `docs/architecture/system-architecture.md` for the system design.

**Subsystem ownership** (matters for who reviews what and who is examined on what):
- **Frontend (`client/`)** — Arthur Gersbacher
- **Backend Spring services (`services/`)** — Johannes Kirchner
- **GenAI service (`services/genai-service/`)** — Luca Kollmer

Cross-subsystem work on CI/CD, Kubernetes, and observability is expected and tracked through PRs.

## Repository Layout

All services and the shared infra now exist. The structure below reflects the repo as it stands.

```
client/                            — React 19 + Vite 8 + TS staff frontend (served at /)
public-report-client/              — React 19 + Vite 8 + TS unauthenticated guest report SPA (served at /report)
edge/                              — nginx front door (compose only) mirroring the k8s ingress: / → client, /report → public-report-client, /api → gateway
services/
  gateway-service/                 — Spring Cloud Gateway, OAuth2 resource server, single API ingress for /api (port 8080; sits behind the edge/k8s ingress)
  auth-service/                    — Spring Boot 4.0.6, Java 21; OAuth2 Authorization Server + Resource Server (8081)
  lost-item-service/               — Spring Boot 4.0.6 (8082)
  found-item-service/              — Spring Boot 4.0.6 (8083)
  matching-service/                — Spring Boot 4.0.6, owns the pgvector index (8084)
  notification-service/            — Spring Boot 4.0.6 (8085)
  operations-service/              — Spring Boot 4.0.6, owns venues + KPI aggregation (8086)
  pickup-service/                  — Spring Boot 4.0.6, magic-link guest pickup scheduling (8087)
  genai-service/                   — Python 3.12 + FastAPI (8000)
api/openapi.yaml                   — contract-first OpenAPI 3.1 spec for genai-service only; Spring services stay code-first via springdoc (see api/README.md)
client/openapi/                    — cached per-service OpenAPI snapshots (auth.json, lost-items.json, found-items.json) feeding Orval
infra/
  prometheus/                      — prometheus.yml, alerts.yml, alerts_test.yml (promtool-tested in CI)
  grafana/                         — provisioning/ + dashboards/ (JSON, mounted by both compose and Helm)
  helm/foundflow/                  — chart for AET + local k8s deploys; see infra/helm/foundflow/README.md
tests/e2e/foundflow-e2e.ps1        — PowerShell E2E suite driven by CI against docker-compose
docs/                              — architecture/, course/, product/, deployment/, research/, diagrams/ (see docs/README.md)
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
npm test               # vitest run (one-shot)
npm run test:watch     # vitest in watch mode
npm run test:coverage  # vitest run --coverage (v8)
```
Stack: React 19, Vite 8, TypeScript 6, Node 22 (pinned in CI), ESLint 10. Tests: Vitest 4 + React Testing Library + jsdom + MSW. **All client tests live under `client/test/`**, mirroring the `client/src/` layout (e.g. a component at `client/src/components/Foo/Foo.tsx` is tested at `client/test/components/Foo/Foo.test.tsx`). Do NOT colocate `.test.*` files next to source — vitest will still pick them up, but they violate the project convention. Test helpers live in `client/test/helpers/` and are imported via the `@test/*` path alias (e.g. `import { renderWithProviders } from '@test/render'`); source is imported via `@/*` as usual. Use `renderWithProviders` for any test that needs `MemoryRouter`, `QueryClientProvider`, or `AuthProvider`. Reference tests: `client/test/pages/Login/Login.test.tsx`, `client/test/auth/{useAuth,RequireAuth}.test.tsx`. CI runs `npm run build` and a `codegen-check` job that fails on drift between `client/openapi/` snapshots and the committed generated client under `client/src/api/`.

**Always use the orval-generated models, requests, and zod schemas** under `client/src/api/**` — never hand-write a type, request DTO, or validation schema that the generated client already provides. For form validation, resolve against the generated `*Body` zod schema (e.g. `loginBody` from `@/api/auth/zod`) and type the form with the generated request interface (e.g. `LoginRequest` from `@/api/auth/model`). If custom UI error copy is needed, extend the generated schema rather than redefining the field contract. Hand-written types are only acceptable for shapes with no OpenAPI representation (e.g. decoded-JWT claims).

### Spring Boot services (`services/<name>/`)
The auth-service is the reference scaffold. All eight Spring services (`gateway-service` + the seven backend services) share the same Gradle setup: Spring Boot 4.0.6, Java 21 toolchain, JUnit 5.
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
`docker compose up` brings up the whole stack: all eight Spring services, the OpenAI-backed genai-service, seven dedicated Postgres 17 instances, RabbitMQ and MinIO, Prometheus 2.55.1, Grafana 11.3.0, the two React SPAs (`client`, `public-report-client`) behind nginx, and the `edge` front door that path-routes to them and the gateway. The default `GENAI_PROVIDER=openai` requires `OPENAI_API_KEY` in the gitignored `.env`; the team shares the development key through Bitwarden. Ollama and its one-shot model-pulling sidecar are optional via `docker compose --profile ollama up` with `GENAI_PROVIDER=local`.

**Host-exposed ports:**
| Service          | Host  | Container |
|------------------|-------|-----------|
| gateway-service  | 8080  | 8080      |
| genai-service    | 8000  | 8000      |
| edge (nginx)     | 3000  | 80        |
| prometheus       | 9090  | 9090      |
| grafana          | 3030  | 3000      |
| minio (API)      | 9000  | 9000      |
| minio (console)  | 9001  | 9001      |
| rabbitmq (AMQP)  | 5672  | 5672      |
| rabbitmq (mgmt)  | 15672 | 15672     |
| mailpit (SMTP)   | 1025  | 1025      |
| mailpit (web/API)| 8025  | 8025      |

The `edge` container on `3000` is the single browser entrypoint: `/` → `client`, `/report` → `public-report-client`, `/api` → `gateway-service`. The two client containers and all Spring backend services (8081–8087) and the seven Postgres DBs are internal-only; reach them through the edge/gateway. RabbitMQ and MinIO additionally expose their ports on the host (broker/management and object-store API/console) for local debugging. `mailpit` is the local/CI SMTP sink: `notification-service` delivers there by default (web UI + REST API on 8025) so test/demo mail never reaches the shared Brevo account. `.env.example` documents the required env vars; copy to `.env` before first run.

## Gateway Routing

`gateway-service` is the single ingress for `/api/**` (the API edge). It sits behind the HTTP front door — the `edge` container in compose, the Kubernetes ingress in the cluster — which path-routes `/` and `/report` to the SPAs and `/api` to the gateway. Gateway routes live in `services/gateway-service/src/main/resources/application.yml`.

| Path prefix                            | Upstream                |
|----------------------------------------|-------------------------|
| `/api/auth/**`, `/api/users/**`        | auth-service:8081       |
| `/api/lost-items/**` | lost-item-service:8082  |
| `/api/found-items/**`                  | found-item-service:8083 |
| `/api/matches/**`                      | matching-service:8084   |
| `/api/notifications/**`                | notification-service:8085 |
| `/api/venues/**`                       | operations-service:8086 |
| `/api/pickups/**`                      | pickup-service:8087     |

Per-service actuator endpoints are exposed under `/{slug}/actuator/{health,prometheus,info}`, and per-service OpenAPI under `/api/{slug}/v3/api-docs` (rewritten downstream by the gateway). Slugs: `auth`, `lost-items`, `found-items`, `matches`, `notifications`, `venues`, `pickups`. The gateway aggregates Swagger UI at `/api/swagger-ui.html` via `springdoc.swagger-ui.urls` — everything lives under `/api` because that is the only prefix the ingress/edge forward to the gateway (`/` and `/report` go to the SPAs).

**Service names matter.** Kubernetes Service objects and docker-compose service keys are identical (`auth-service`, `lost-item-service`, …) so the gateway's hardcoded upstream URIs work in compose, local k8s, and AET without per-env config.

## Architecture (the parts that span files)

The system is **event-driven with synchronous edges**. The intake → matching segment of the bus is wired; downstream segments (notification, operations projections) are still planned.

- **REST/JSON over HTTP** is the synchronous edge: user-facing commands/queries through the gateway, plus synchronous calls to `genai-service` for attribute extraction, embedding, and match verification.
- **RabbitMQ is live in docker-compose** (`rabbitmq:4.0-management`; AMQP on 5672 and the management UI on 15672 are host-exposed for local debugging). All routing constants are shared in `shared/domain-events/.../FoundFlowEventRouting.java`: a single topic exchange `foundflow.domain-events`, with routing keys `lost-report.created.v1`, `lost-report.updated.v1`, `found-item.logged.v1`, `found-item.updated.v1`, and `match-candidate.created.v1`. Publishers (`lost-item-service`, `found-item-service`) and the consumer (`matching-service`, which declares the durable `matching.*.v1` queues and bindings in `AmqpConfig`) both import these constants, so producer keys and consumer bindings cannot drift.
- **Wired flow:** `LostReportService`/`FoundItemService` save the item, call genai `/extract-attributes` (best-effort — failures are swallowed so intake never blocks), then publish the domain event. `matching-service`'s `IntakeEventListener` → `CandidateMatchingService.processIntake()` calls genai `/embed`, upserts the vector into `item_embeddings` (pgvector, HNSW, idempotent on `(item_type, item_id)`), runs the `<=>` cosine KNN against the opposite item type, scores `combinedScore = categoryGate × (1 − distance)`, persists a `Match` (PENDING) above the threshold (default `0.55`), publishes `match-candidate.created.v1`, and asynchronously calls `MatchVerificationService.verifyAsync()` (genai `/verify-match`) to record the verification verdict on the match.
- **Still planned** (not yet wired): the `MatchConfirmed`, `NotificationSent`, and `CaseClosed` events and any consumer of `match-candidate.created.v1` (e.g. `notification-service`).
- **Caveat for local/E2E:** the default-in-tests `fake` genai provider returns a constant embedding vector for every item, so semantic ranking is meaningless and matching collapses to the category gate. Real ranking requires `GENAI_PROVIDER=local` (Ollama) or `openai`; real-model behavior is only exercised by the nightly, non-blocking `openai-integration.yml`.

**Database isolation is strict.** Each Spring service owns its own PostgreSQL 17 database with its own DB user; services never read each other's tables. Cross-service detail reads go through REST. The `matching-service` database adds the `pgvector` extension and stores embeddings produced by `genai-service`. Migrations are managed per-service with Flyway.

**OpenAPI 3.1 is the contract for sync APIs.** `api/openapi.yaml` covers the GenAI service only (issue #48); the Spring services are code-first via springdoc — each exposes `/v3/api-docs`, aggregated through the gateway Swagger UI (see `api/README.md`). The Orval-driven frontend client reads cached per-service snapshots from `client/openapi/*.json` (refreshed via `npm run codegen:fetch` against a running gateway). The `codegen-check` CI job guards against drift. Do not hand-write DTOs that the spec covers, and do not introduce direct cross-service HTTP calls without the generated client.

**Auth.** `auth-service` is a Spring OAuth2 Authorization Server (issues JWTs) and Resource Server. `gateway-service` is a Resource Server that validates JWTs against the auth-service JWK set (`/oauth2/jwks`). `auth-service` owns credentials, user records, sessions, and refresh-token state. `operations-service` owns venue records and KPI aggregation, and authorizes access from JWT roles plus the `venue_id` claim.

**Provider switch for the LLM.** `GENAI_PROVIDER=openai|local|fake` selects the OpenAI API, a local Ollama backend, or the deterministic `fake` provider used in tests/E2E. OpenAI is the local Compose default and requires the Bitwarden-shared `OPENAI_API_KEY`; Ollama is an explicit optional Compose profile. Implementations live in `app/providers/{openai,ollama,fake}.py` and share the same provider interface — same code path, no separate implementations. The `local`/Ollama path is an offline, no-key escape hatch (`docker compose --profile ollama up`); its adapter is guarded by the blocking `test_provider_contract.py` + `test_provider_ollama_dimensions.py` unit tests. Real-model behavior against a live backend is exercised by the nightly, non-blocking `openai-integration.yml`.

The embedding dimensionality is a separate `EMBEDDING_DIMENSIONS` env var (default `768`). It drives the `item_embeddings.embedding` column type via a Flyway placeholder (`spring.flyway.placeholders.embedding_dim`), the OpenAI `dimensions=` parameter, and a startup probe in both services. When changing `OPENAI_EMBED_MODEL` or `OLLAMA_EMBED_MODEL`, also update `EMBEDDING_DIMENSIONS` to match the new model's output — both services refuse to start otherwise. The matching-service migrations under `db/migration/*.sql` are now the first in the repo to use Flyway placeholders; editing one of them requires `flyway repair` or `docker compose down -v --remove-orphans` on any local dev DB once.

**Object storage abstraction.** Implemented in `shared/photo-storage/` as a single `PhotoStorage` interface with three adapters — `MinioPhotoStorage` (compose default), `AzurePhotoStorage` (cloud), and `FileSystemPhotoStorage` (test/fallback) — selected via `PHOTO_STORAGE_PROVIDER`. Both `lost-item-service` and `found-item-service` inject the interface; signed-URL TTL is configurable via `photo-storage.signed-url-ttl` (default `PT10M`). Detailed in `docs/architecture/photo-storage.md`.

## CI Workflows

`.github/workflows/ci.yml` runs on push/PR to `development` and `main`. Jobs:

1. **backend-tests** — matrix over all eight Spring services (including `gateway-service` and `pickup-service`), runs `./gradlew check`.
2. **genai-tests** — `ruff check` + `pytest -v` on Python 3.12 with pip cache keyed on `pyproject.toml`.
3. **prometheus-rules-tests** — `promtool test rules` against `infra/prometheus/alerts_test.yml` via the `prom/prometheus:v2.55.1` image.
4. **client-build** — `npm ci && npm run build` on Node 22.
5. **client-tests** — `npm ci && npm run test:coverage` (Vitest) on Node 22; uploads the `client-coverage` artifact.
6. **codegen-check** — regenerates the Orval client from `client/openapi/` and fails on `git diff --exit-code client/src/api`.
7. **e2e-tests** — depends on `backend-tests`, `client-build`, and `client-tests`. Boots the backend stack via `docker compose up --build -d`, polls health endpoints through the gateway, then runs `./tests/e2e/foundflow-e2e.ps1` (PowerShell). Tears down with `docker compose down -v --remove-orphans`.

`.github/workflows/openai-integration.yml` — nightly (05:30 UTC) + on-push-to-main + `workflow_dispatch`, hits real OpenAI via `secrets.OPENAI_API_KEY`, non-blocking. Surfaces a model rename, SDK bump, or `text-embedding-3-small` deprecation here instead of at Azure deploy time. Boots `genai-service` standalone and asserts `/_diagnostic` reports `embed_dimensions_configured=embed_dimensions_actual=768` against the real API before running `tests/integration/test_real_openai_provider.py`. (The OpenAI provider is what local Compose and deployment use; the `local`/Ollama provider has no live-backend nightly — its adapter is covered by blocking unit tests only.)

CD to Kubernetes is **wired**: `.github/workflows/aet-helm-deploy.yml` builds and pushes images to GHCR and runs `helm upgrade --install` against the AET (Rancher RKE2) cluster on every push to `main` (and via `workflow_dispatch`) — live at `team-chaos-monkeys.stud.k8s.aet.cit.tum.de`. `.github/workflows/azure-cycle.yml` provisions, deploys to, and destroys an ephemeral Azure VM on demand. The Helm chart (`infra/helm/foundflow/`) is the deploy artifact; `values-aet.yaml` is the production override layer.

## Engineering Constraints from the Course Brief

These are graded requirements, not preferences. Violating them costs points:

- **Three-or-fewer-commands local startup.** `docker compose up` must run the full system end-to-end with sane defaults. No long ENV setup.
- **No hardcoded credentials anywhere.** Local: gitignored `.env` files (`.env.example` documents required vars). Kubernetes: `ConfigMap` for non-secret config, `Secret` for credentials populated from GitHub repository secrets via CI.
- **Every component has its own Dockerfile.** Postgres uses the official image; the compose file declares it.
- **Mandatory PRs.** No direct commits to `main`. Each feature/bugfix lives on its own branch, CI must pass, peer review is required, branches stay short-lived (target ≤2 days).
- **CI must build and test all services on every PR.** CD must auto-deploy to Kubernetes on merge to `main`. CI (`ci.yml`) runs on every PR; CD (`aet-helm-deploy.yml`) runs `helm upgrade` to AET on merge to `main`.
- **Deployable to both Rancher (course infra) and Azure** via Helm. The `infra/helm/foundflow/` chart targets AET (Rancher RKE2) via `values-aet.yaml` and local Kubernetes via `values-local.yaml`.
- **Observability must be meaningful.** Prometheus tracks request count, latency, and error rate at minimum, plus domain metrics (matches/min, GenAI extraction latency, vector search latency). Grafana dashboards committed as JSON under `infra/grafana/dashboards/` (shared by compose and Helm). Alert rules in `infra/prometheus/alerts.yml` are promtool-tested in CI; the Helm chart re-renders them as a `PrometheusRule` CR.
- **Spring services expose `/actuator/prometheus`** via Micrometer; the Python service exposes `/metrics`.
- **OpenAPI/Swagger UI** is exposed by each Spring service (`/v3/api-docs`, `/swagger-ui.html`) and aggregated by the gateway.

## Git Workflow

- Feature, chore, and fix branches are cut from `development` and merged back into `development` via PR. Default `gh pr create --base development`.
- `main` is the release branch. Merging `development → main` is what triggers CD (when CD lands) — only do it when releasing, never as the PR base for ordinary work.
- Start work with `git fetch origin && git checkout -b <branch> origin/development`.
- CI runs on both branches; protect `main` with required-checks once CD is live.

## Issue Hierarchy (Epics & Sub-Issues)

Every issue must be linked into the epic tree via GitHub's **sub-issue** relationship — not just a `Part of #N` mention in the body. Plain-text references do **not** nest issues under their epic in GitHub Projects; only a real parent link makes the work roll up on the board. The top-level subsystem epics are: **#7 GenAI**, plus the per-subsystem epics for frontend and backend.

When you open or triage an issue, set its parent immediately:

```sh
# Get node IDs (parent epic + child issue)
gh api graphql -F owner=AET-DevOps26 -F name=team-chaos-monkeys -f query='
  query($owner:String!,$name:String!){repository(owner:$owner,name:$name){
    parent:issue(number:7){id} child:issue(number:177){id parent{number}}}}'

# Link child under parent
gh api graphql -H "GraphQL-Features: sub_issues" \
  -F parent="<parent node id>" -F child="<child node id>" \
  -f query='mutation($parent:ID!,$child:ID!){addSubIssue(input:{issueId:$parent,subIssueId:$child}){subIssue{number parent{number}}}}'
```

Nest epics under epics where appropriate (e.g. `#7 → #177 → #178/#179/#180`). A `Part of #N` line in the body is fine as human-readable context, but it is **not** a substitute for the link.

## When Adding a New Spring Service

1. Mirror `services/auth-service/` (Gradle wrapper, Spring Boot 4.0.6, Java 21, JUnit 5).
2. Pick a non-conflicting port in `application.properties` (current allocations: 8080–8087).
3. Wire up a dedicated PostgreSQL 17 database in `docker-compose.yml` *and* `infra/helm/foundflow/values.yaml` (`databases:` + `services.<svc>.dbRef`) — never share with another service.
4. Add the service to the CI matrix in `.github/workflows/ci.yml` (`backend-tests` job).
5. Add a gateway route in `services/gateway-service/src/main/resources/application.yml` covering API paths + the three actuator paths + `/v3/api-docs`. Register the docs URL under `springdoc.swagger-ui.urls`.
6. Expose Actuator + Micrometer Prometheus endpoint; the Helm chart's `ServiceMonitor` template will pick it up automatically when `services.<svc>.metrics: true` (default for `kind: spring`).
7. Keep the service code-first via springdoc: expose `/v3/api-docs`, register it under the gateway's `springdoc.swagger-ui.urls`, and snapshot it into `client/openapi/<svc>.json` for Orval. `api/openapi.yaml` stays genai-only unless #61 changes that.
8. Emit/consume domain events through RabbitMQ (now live in compose; routing constants in `shared/domain-events`) rather than chaining synchronous calls when the work is asynchronous (intake, matching, notifications, audit projections).

# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Context

FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues, developed as the team project for TUM's DevOps course (CIT423001, summer term 2026). It is a graded, mono-repo deliverable: the *engineering process* (CI/CD, observability, reproducible deploys) is graded as heavily as the application itself. See `docs/task-description.md` for the binding course requirements, `docs/problem-statement.md` for the domain framing, and `docs/architecture.md` for the system design.

**Subsystem ownership** (matters for who reviews what and who is examined on what):
- **Frontend (`client/`)** — Arthur Gersbacher
- **Backend Spring services (`services/`)** — Johannes Kirchner
- **GenAI service (Python, not yet present)** — Luca Kollmer

Cross-subsystem work on CI/CD, Kubernetes, and observability is expected and tracked through PRs.

## Repository Layout

This is an early-stage scaffold. Most directories below do not exist yet but are committed to in `docs/architecture.md` and should be created in the right place when work begins.

```
client/                        — React + Vite + TS frontend (exists)
services/
  auth-service/                — Spring Boot 3, Java 21 (exists, scaffold only)
  lost-item-service/           — planned
  found-item-service/          — planned
  matching-service/            — planned (owns pgvector index)
  notification-service/        — planned
  operations-service/          — planned
  genai-service/               — planned, Python 3.12 + FastAPI
api/openapi.yaml               — planned, single source of truth for sync APIs
infra/                         — planned, docker-compose + Helm + Grafana dashboards
docs/                          — architecture, problem statement, task brief
```

## Common Commands

### Frontend (`client/`)
```
npm install
npm run dev       # Vite dev server
npm run build     # tsc -b && vite build
npm run lint      # eslint .
npm run preview   # serve built bundle
```
Stack: React 19, Vite 8, TypeScript 6, ESLint 10. No test runner is wired up yet — when adding one, prefer Vitest (it composes with Vite).

### Spring Boot services (`services/<name>/`)
The auth-service is the reference scaffold; new services should mirror its Gradle setup (Spring Boot 4.0.6, Java 21 toolchain, JUnit 5).
```
./gradlew bootRun                          # run the service
./gradlew build                            # compile + test + assemble
./gradlew test                             # all tests
./gradlew test --tests '*HelloController*' # single test class/method (Gradle pattern)
```
Default port is `8080` (set in `application.properties`); change per service to avoid local port clashes when multiple services run together.

### GenAI service (planned)
Python 3.12 + FastAPI, exposing `/metrics` via `prometheus_client`. Will be containerised with its own Dockerfile and orchestrated via the root `docker-compose.yml`.

## Architecture (the parts that span files)

The system is **event-driven with synchronous edges**. Get this distinction right when wiring new flows:

- **REST/JSON over HTTP** is used for: user-facing commands and queries from the frontend, and synchronous calls to the `genai-service` (attribute extraction, embedding, message generation).
- **RabbitMQ domain events** (`LostReportCreated`, `FoundItemLogged`, `MatchCandidateCreated`, `MatchConfirmed`, `NotificationSent`, `CaseClosed`) carry async workflows: intake → matching → notification → operations projections. Services do **not** chain through synchronous calls for these flows.

**Database isolation is strict.** Each Spring service owns its own PostgreSQL database with its own DB user; services never read each other's tables. Cross-service detail reads go through REST. The `matching-service` database adds the `pgvector` extension and stores embeddings produced by `genai-service`. Migrations are managed per-service with Flyway.

**OpenAPI 3.1 is the contract for sync APIs.** A single `api/openapi.yaml` (planned) generates Spring stubs, the Python client, and the TS SDK. Do not hand-write DTOs that should come from codegen, and do not introduce direct cross-service HTTP calls without the generated client. Event payloads are versioned and documented alongside the OpenAPI spec.

**Auth split.** `auth-service` owns credentials, sessions, refresh tokens, and JWT issuance. `operations-service` owns staff/ops/admin user *profiles*. They are linked by `authSubject` — keep this boundary; don't put profile fields in `auth-service` or credential state in `operations-service`.

**Provider switch for the LLM.** `GENAI_PROVIDER=openai|local` toggles between OpenAI API and a local model (Ollama/LLaMA). The same code path must serve both — no separate implementations.

**Object storage abstraction.** MinIO locally, Azure Blob in cloud. Define and reuse a single photo-storage interface shared by `lost-item-service` and `found-item-service` before implementations land — this is called out as a known risk in `docs/architecture.md`.

## Engineering Constraints from the Course Brief

These are graded requirements, not preferences. Violating them costs points:

- **Three-or-fewer-commands local startup.** `docker compose up` must run the full system end-to-end with sane defaults. No long ENV setup.
- **No hardcoded credentials anywhere.** Local: gitignored `.env` files. Kubernetes: `ConfigMap` for non-secret config, `Secret` for credentials populated from GitHub repository secrets via CI.
- **Every component has its own Dockerfile.** Including the database (use the official image; no custom builds needed, but the compose file must declare it).
- **Mandatory PRs.** No direct commits to `main`. Each feature/bugfix lives on its own branch, CI must pass, peer review is required, branches stay short-lived (target ≤2 days).
- **CI must build and test all services on every PR.** CD must auto-deploy to Kubernetes on merge to `main`. Both via GitHub Actions.
- **Deployable to both Rancher (course infra) and Azure** via Helm charts.
- **Observability must be meaningful.** Prometheus tracks request count, latency, and error rate at minimum, plus domain metrics (matches/min, GenAI extraction latency, vector search latency). Grafana dashboards committed as JSON under `infra/grafana/dashboards/`. At least one alert rule (e.g. service-down or 5xx > 1% over 5 min) configured.
- **Spring services expose `/actuator/prometheus`** via Micrometer; the Python service exposes `/metrics`.
- **OpenAPI/Swagger UI** must be exposed by the backend for API documentation.

## Git Workflow

- Feature, chore, and fix branches are cut from `development` and merged back into `development` via PR. Default `gh pr create --base development`.
- `main` is the release branch. Merging `development → main` is what triggers CD — only do it when releasing, never as the PR base for ordinary work.
- Start work with `git fetch origin && git checkout -b <branch> origin/development`.

## When Adding a New Spring Service

1. Mirror `services/auth-service/` (Gradle wrapper, Spring Boot 4.0.6, Java 21, JUnit 5).
2. Pick a non-conflicting port in `application.properties`.
3. Wire up a dedicated PostgreSQL database in `docker-compose.yml` and the Helm chart — never share with another service.
4. Generate controller stubs from `api/openapi.yaml` once it exists; don't hand-write request/response DTOs that the spec covers.
5. Expose Actuator + Micrometer Prometheus endpoint and label the K8s service/pod with `monitoring: "true"` so the shared Prometheus scraper picks it up.
6. Emit/consume domain events through RabbitMQ rather than chaining synchronous calls when the work is asynchronous (intake, matching, notifications, audit projections).

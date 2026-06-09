# Team Chaos Monkeys — FoundFlow

Mono-repo for the DevOps course project (CIT423001) at TUM, summer term 2026. FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues — see [`docs/product/problem-statement.md`](docs/product/problem-statement.md) for the domain framing and [`docs/architecture/system-architecture.md`](docs/architecture/system-architecture.md) for the system design.

## Team

| Name | Subsystem | Owns |
|---|---|---|
| Arthur Gersbacher | Frontend | `client/` |
| Johannes Kirchner | Backend Spring services | `services/{gateway,auth,lost-item,found-item,matching,pickup,notification,operations}-service/` |
| Luca Kollmer | GenAI service | `services/genai-service/` |

Subsystem ownership defines who is primarily responsible for design, implementation, and the individual oral examination at the end of the term. Cross-subsystem collaboration on integration, CI/CD, and observability is expected and tracked through pull requests.

## Run locally

The full stack — frontend, gateway, seven Spring services, GenAI service, seven isolated Postgres databases, and an Ollama LLM runtime — boots from one Compose file. You need Docker Desktop (or any engine with Compose v2.24+) and roughly 6 GB of free RAM.

```bash
cp .env.example .env          # one-time, gitignored
docker compose up --build     # builds and starts everything
```

First boot pulls Ollama models, which takes a few minutes; subsequent boots reuse the cached models in a Docker volume. Once `docker compose ps` shows everything healthy:

| URL | What it is |
|---|---|
| http://localhost:3000 | Frontend (React) |
| http://localhost:8080 | API gateway — single entry point for all backend calls |
| http://localhost:8080/swagger-ui.html | Aggregated OpenAPI UI for the Spring services, proxied through the gateway |
| http://localhost:8081/swagger-ui.html | `auth-service` OpenAPI UI (direct) |
| http://localhost:8000/docs | `genai-service` FastAPI docs |
| http://localhost:8000/metrics | `genai-service` Prometheus scrape endpoint |
| http://localhost:9090 | Prometheus — scrape targets and alert rules (see [Observability](#observability)) |
| http://localhost:3030 | Grafana — Services — RED dashboard, default credentials `admin`/`admin` |
| http://localhost:8025 | Mailpit — captured outbound email (local/CI SMTP sink; nothing reaches the real Brevo account) |

`auth-service` is the only Spring service besides the gateway with a host port mapping — by design, the gateway is the sole public entry point for the other Spring services (`lost-item`, `found-item`, `matching`, `pickup`, `notification`, `operations`), so their ports stay inside the Compose network.

To stop and clean up volumes: `docker compose down -v`.

### Provider switch for GenAI

`GENAI_PROVIDER=local` (default) talks to the bundled Ollama container with the chat/embed models named in `.env`. Setting `GENAI_PROVIDER=openai` with an `OPENAI_API_KEY` switches to OpenAI without changing any code paths.

## Architecture

The system is **event-driven with synchronous edges**:

- **REST/JSON** carries user-facing commands and queries from the frontend, public magic-link flows for match confirmation and pickup scheduling, and synchronous calls to `genai-service` (attribute extraction, embedding, match verification).
- **Domain events** (`LostReportCreated`, `FoundItemLogged`, `MatchCandidateCreated`, `MatchConfirmed`, …) carry async intake → matching → notification workflows. The bus is RabbitMQ — live in compose and wired for intake → matching (see [`docs/architecture/messaging-and-events.md`](docs/architecture/messaging-and-events.md)).

Each Spring service owns its own Postgres database; services never read each other's tables. Cross-service detail reads go through REST behind the gateway. Migrations are per-service Flyway. The `matching-service` database adds `pgvector` and stores embeddings produced by `genai-service`; the `pickup-service` database owns pickup schedules, booked pickups, and local pickup-email logs.

Authentication is OAuth2 (authorization-code + PKCE) with JWT bearer tokens. `auth-service` issues tokens carrying `roles` and `venue_id` claims; downstream services enforce tenancy by venue. Role and endpoint matrix: [`docs/architecture/api-and-security.md`](docs/architecture/api-and-security.md).

Photo storage uses a shared abstraction so the same code targets MinIO locally and Azure Blob in the cloud — design rationale in [`docs/architecture/photo-storage.md`](docs/architecture/photo-storage.md).

## Repository layout

```
.
├── api/
│   └── openapi.yaml            — GenAI service contract (Spring paths land via #61)
├── client/                     — React + Vite + TypeScript frontend
├── docs/                       — architecture/, course/, product/, deployment/, research/, diagrams/
├── services/
│   ├── gateway-service/        — Spring Cloud Gateway, edge routing
│   ├── auth-service/           — OAuth2 authorization server + user management
│   ├── lost-item-service/      — guest lost-item reports
│   ├── found-item-service/     — staff found-item intake
│   ├── matching-service/       — pgvector-backed match candidates
│   ├── pickup-service/         — pickup schedules, public pickup booking, local email logs
│   ├── notification-service/   — guest pickup notifications
│   ├── operations-service/     — venue records + KPI aggregation
│   └── genai-service/          — Python 3.12 + FastAPI; extraction, embedding, verification
├── shared/
│   └── common-domain/          — shared domain types
├── tests/
│   └── e2e/                    — Docker Compose end-to-end test harness (PowerShell)
├── .devcontainer/              — unified VS Code dev environment
└── docker-compose.yml          — full-stack local boot (one command)
```

## Development

### Frontend (`client/`)

```bash
cd client
npm install
npm run dev             # Vite dev server on http://localhost:3000
npm run build           # tsc -b && vite build
npm run codegen         # regenerate Orval clients from the cached specs in client/openapi/
npm run codegen:fetch   # refresh client/openapi/*.json from the running gateway, then regenerate
```

Stack: React 19, Vite 8, TypeScript 6. The typed API client is generated by Orval from snapshots of each Spring service's springdoc output (cached under `client/openapi/`, refreshed via `codegen:fetch` against `http://localhost:8080/{prefix}/v3/api-docs`) — never hand-write request/response types Orval generates. CI's `codegen-check` job fails if the committed client drifts.

### Spring services (`services/<name>-service/`)

```bash
cd services/<name>-service
./gradlew bootRun                            # run this service alone
./gradlew test
./gradlew check                              # full pre-commit check (what CI runs)
```

Stack: Spring Boot 4.0.6, Java 21 toolchain, JUnit 5. Each service has its own port (see Run locally), its own Postgres database, and its own Flyway migrations. Actuator + Micrometer Prometheus are wired on every service; OpenAPI docs are exposed via `springdoc`.

When adding a new Spring service, mirror an existing scaffold (Gradle wrapper, dependencies, security config, Flyway baseline) and assign a non-conflicting port.

### GenAI service (`services/genai-service/`)

```bash
cd services/genai-service
pip install -e '.[dev]'
pytest -v
uvicorn app.main:app --reload --port 8000
```

Stack: Python 3.12, FastAPI, `prometheus_client`. Ships unit tests, a provider-contract test (works against fake/openai/ollama), and a golden attribute-extraction test set (`tests/golden/`).

### Devcontainer

`.devcontainer/` defines a VS Code dev container with all three toolchains (Java 21, Node 22, Python 3.12) pre-installed. The container is the *toolbox*; the runtime stack (databases, services, Ollama) is still launched separately with `docker compose up` against the host Docker socket.

## API contract

The project runs a mixed contract model today; [#61](https://github.com/AET-DevOps26/team-chaos-monkeys/issues/61) tracks the planned reconciliation.

- **GenAI service** — contract-first against `api/openapi.yaml`. `services/genai-service/app/api/schemas.py` mirrors the spec, and `services/genai-service/tests/golden/_contract.py` enforces alignment. Lint with `npx @redocly/cli lint api/openapi.yaml`; preview with `npx @redocly/cli preview-docs api/openapi.yaml` (config in `redocly.yaml`).
- **Spring services** — code-first via springdoc. Controllers are hand-written and `/v3/api-docs` is generated from the annotations; the gateway aggregates them at `http://localhost:8080/swagger-ui.html`.
- **Frontend** — Orval generates the typed React-Query client from snapshots of the Spring services' springdoc output cached under `client/openapi/`. Refresh and regenerate with `cd client && npm run codegen:fetch`.

## Per-developer Compose overrides

If a host port clashes with something else on your machine (e.g. another project already on `3000`), **don't edit `docker-compose.yml`** — copy [`docker-compose.override.yml.example`](docker-compose.override.yml.example) to `docker-compose.override.yml` (gitignored) and adjust there. Compose automatically merges that file with `docker-compose.yml` on every `docker compose up` — no flags, no env vars, no committed-file edits.

> Compose merges list-type fields (`ports`, `volumes`) by appending, not replacing. To swap a published port cleanly, use the `!override` tag — the example file shows the pattern. Requires Compose v2.24+.

## CI/CD and branching

- **Branches:** `feature/*`, `chore/*`, `fix/*` cut from `development`. PRs target `development`; merge via pull request only.
- **Releases:** `main` is reserved for release PRs from `development`; the automated deploy on merge is planned (see below) but not wired up yet.
- **CI** (`.github/workflows/ci.yml`): runs on every PR — Gradle `check` for each backend service (matrix), pytest + ruff for `genai-service`, Vite build for the client, Orval drift check, and a full `docker compose up` + E2E test pass.
- **Continuous deployment** to Rancher (course infrastructure) and Azure is planned; Helm charts already live under `infra/helm/`, and the remaining work is wiring automated deploys.

## Observability

`docker compose up` brings up a Prometheus + Grafana stack that scrapes every FoundFlow service out of the box:

| URL | What it is |
|---|---|
| http://localhost:9090 | Prometheus — `/targets` for scrape health, `/alerts` for the configured rules (`ServiceDown`, `HighErrorRate`) |
| http://localhost:3030 | Grafana — log in with `admin`/`admin` (override via `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` in `.env`). The provisioned **Services — RED** dashboard is the default view. |

Every Spring service exposes `/actuator/prometheus` (Micrometer); `genai-service` exposes `/metrics` (`prometheus_client` + `prometheus-fastapi-instrumentator`). Scrape config lives under `infra/prometheus/`; provisioned dashboards under `infra/grafana/dashboards/`. Per-service RED metrics are wired today; domain metrics (matches/min, GenAI extraction latency, vector search latency) are added incrementally as features ship.

## Documentation index

- [`docs/README.md`](docs/README.md) — documentation index
- [`docs/product/problem-statement.md`](docs/product/problem-statement.md) — domain framing
- [`docs/architecture/system-architecture.md`](docs/architecture/system-architecture.md) — system design (diagrams under `docs/diagrams/`)
- [`docs/architecture/api-and-security.md`](docs/architecture/api-and-security.md) — roles, claims, endpoint permissions
- [`docs/architecture/photo-storage.md`](docs/architecture/photo-storage.md) — storage abstraction shared by lost/found item services
- [`docs/course/requirements.md`](docs/course/requirements.md) — course requirements & grading

# Team Chaos Monkeys — FoundFlow

Mono-repo for the DevOps course project (CIT423001) at TUM, summer term 2026. FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues — see [`docs/problem-statement.md`](docs/problem-statement.md) for the domain framing and [`docs/architecture.md`](docs/architecture.md) for the system design.

## Team

| Name | Subsystem | Owns |
|---|---|---|
| Arthur Gersbacher | Frontend | `client/` |
| Johannes Kirchner | Backend Spring services | `services/{gateway,auth,lost-item,found-item,matching,notification,operations}-service/` |
| Luca Kollmer | GenAI service | `services/genai-service/` |

Subsystem ownership defines who is primarily responsible for design, implementation, and the individual oral examination at the end of the term. Cross-subsystem collaboration on integration, CI/CD, and observability is expected and tracked through pull requests.

## Run locally

The full stack — frontend, gateway, six Spring services, GenAI service, six isolated Postgres databases, and an Ollama LLM runtime — boots from one Compose file. You need Docker Desktop (or any engine with Compose v2.24+) and roughly 6 GB of free RAM.

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

`auth-service` is the only Spring service besides the gateway with a host port mapping — by design, the gateway is the sole public entry point for the other Spring services (`lost-item`, `found-item`, `matching`, `notification`, `operations`), so their ports stay inside the Compose network.

To stop and clean up volumes: `docker compose down -v`.

### Provider switch for GenAI

`GENAI_PROVIDER=local` (default) talks to the bundled Ollama container with the chat/embed models named in `.env`. Setting `GENAI_PROVIDER=openai` with an `OPENAI_API_KEY` switches to OpenAI without changing any code paths.

## Architecture

The system is **event-driven with synchronous edges**:

- **REST/JSON** carries user-facing commands and queries from the frontend and synchronous calls to `genai-service` (attribute extraction, embedding, match verification).
- **Domain events** (`LostReportCreated`, `FoundItemLogged`, `MatchCandidateCreated`, `MatchConfirmed`, …) carry async intake → matching → notification workflows. The bus is RabbitMQ (planned — wired in a later milestone; see [`docs/architecture.md`](docs/architecture.md)).

Each Spring service owns its own Postgres database; services never read each other's tables. Cross-service detail reads go through REST behind the gateway. Migrations are per-service Flyway. The `matching-service` database adds `pgvector` and stores embeddings produced by `genai-service`.

Authentication is OAuth2 (authorization-code + PKCE) with JWT bearer tokens. `auth-service` issues tokens carrying `roles` and `venue_id` claims; downstream services enforce tenancy by venue. Role and endpoint matrix: [`docs/api-security.md`](docs/api-security.md).

Photo storage uses a shared abstraction so the same code targets MinIO locally and Azure Blob in the cloud — design rationale in [`docs/photo-storage.md`](docs/photo-storage.md).

## Repository layout

```
.
├── api/
│   └── openapi.yaml            — single source of truth for sync APIs
├── client/                     — React + Vite + TypeScript frontend
├── docs/                       — architecture, problem statement, security model
├── services/
│   ├── gateway-service/        — Spring Cloud Gateway, edge routing
│   ├── auth-service/           — OAuth2 authorization server + user management
│   ├── lost-item-service/      — guest lost-item reports
│   ├── found-item-service/     — staff found-item intake
│   ├── matching-service/       — pgvector-backed match candidates
│   ├── notification-service/   — guest pickup notifications
│   ├── operations-service/     — staff profiles + venue KPIs
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
npm run dev          # Vite dev server on http://localhost:3000
npm run lint
npm run build        # tsc -b && vite build
npm run codegen      # regenerate Orval clients from api/openapi.yaml
```

Stack: React 19, Vite 8, TypeScript 6. The typed API client is generated by Orval — never hand-write request/response types for endpoints the OpenAPI spec covers. CI's `codegen-check` job fails if the committed client drifts.

### Spring services (`services/<name>-service/`)

```bash
cd services/<name>-service
./gradlew bootRun                            # run this service alone
./gradlew test
./gradlew test --tests '*HelloController*'   # single class/method
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

`api/openapi.yaml` is the single source of truth for synchronous APIs. The frontend client (Orval), Spring controller signatures, and the Python client are all derived from it. Edit the spec, regenerate downstream artifacts:

```bash
cd client && npm run codegen
```

A local Redocly preview can be opened with `npx @redocly/cli preview-docs api/openapi.yaml` (config in `redocly.yaml`).

## Per-developer Compose overrides

If a host port clashes with something else on your machine (e.g. another project already on `3000`), **don't edit `docker-compose.yml`** — copy [`docker-compose.override.yml.example`](docker-compose.override.yml.example) to `docker-compose.override.yml` (gitignored) and adjust there. Compose automatically merges that file with `docker-compose.yml` on every `docker compose up` — no flags, no env vars, no committed-file edits.

> Compose merges list-type fields (`ports`, `volumes`) by appending, not replacing. To swap a published port cleanly, use the `!override` tag — the example file shows the pattern. Requires Compose v2.24+.

## CI/CD and branching

- **Branches:** `feature/*`, `chore/*`, `fix/*` cut from `development`. PRs target `development`; merge via pull request only.
- **Releases:** merging `development → main` triggers continuous deployment to Kubernetes. Reserve `main` for release PRs.
- **CI** (`.github/workflows/ci.yml`): runs on every PR — Gradle `check` for each backend service (matrix), pytest for `genai-service`, Vite build for the client, Orval drift check, and a full `docker compose up` + E2E test pass.
- **Continuous deployment** to Rancher (course infrastructure) and Azure is planned via Helm charts under `infra/` (to be added in a later milestone).

## Observability

- Every Spring service exposes `/actuator/prometheus`; `genai-service` exposes `/metrics`.
- Per-service base metrics (request count, latency, error rate) are exported by Micrometer; domain metrics (matches/min, GenAI extraction latency, vector search latency) are added incrementally as features ship.
- Prometheus and Grafana scrape configuration plus committed dashboards (`infra/grafana/dashboards/`) are planned in a later milestone.

## Documentation index

- [`docs/problem-statement.md`](docs/problem-statement.md) — domain framing
- [`docs/architecture.md`](docs/architecture.md) — system design and UML
- [`docs/api-security.md`](docs/api-security.md) — roles, claims, endpoint permissions
- [`docs/photo-storage.md`](docs/photo-storage.md) — storage abstraction shared by lost/found item services
- [`docs/task-description.md`](docs/task-description.md) — original course brief (do not edit)

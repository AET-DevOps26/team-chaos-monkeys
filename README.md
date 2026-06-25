# Team Chaos Monkeys — FoundFlow

Mono-repo for the DevOps course project (CIT423001) at TUM, summer term 2026. FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues — see [`docs/product/problem-statement.md`](docs/product/problem-statement.md) for the domain framing and [`docs/architecture/system-architecture.md`](docs/architecture/system-architecture.md) for the system design.

## Team

| Name | Subsystem | Owns |
|---|---|---|
| Arthur Gersbacher | Frontend | `client/` |
| Johannes Kirchner | Backend Spring services | `services/{gateway,auth,lost-item,found-item,matching,pickup,notification,operations}-service/` |
| Luca Kollmer | GenAI service | `services/genai-service/` |

Subsystem ownership defines who is primarily responsible for design, implementation, and the individual oral examination at the end of the term. Cross-subsystem collaboration on integration, CI/CD, and observability is expected and tracked through pull requests.

## Reviewer walkthrough

A short path through the system, mapped to the graded requirements. Demo data is seeded
on first boot, so a working match is visible the moment you log in.

**1. Start** (first boot ~10–15 min — wait until login succeeds). Drop the shared
`.env` into the repo root — we send you a Bitwarden link to a ready-to-use `.env`
with all secret keys (incl. `OPENAI_API_KEY`) — then:

```bash
docker compose up --build
```

If the reviewer wants to verify the Helm/local-Kubernetes path instead, follow
[`docs/deployment/local-kubernetes.md`](docs/deployment/local-kubernetes.md)
from Git Bash on Windows:

```bash
make -C infra/helm kube-quickstart ADMIN_EMAIL=admin@foundflow.local ADMIN_PASSWORD=admin12345 OPENAI_API_KEY=sk-...
```

That path serves the app at http://foundflow.localtest.me/ and Grafana at
http://foundflow.localtest.me/grafana/.

**2. Log in** at http://localhost:3000 with the seeded staff account
`staff.demo@foundflow.local` / `test12345` and review the app from the normal
venue staff perspective. The admin account `admin@foundflow.local` / `admin12345`
is still available for user/admin checks.

**3. See it working — GenAI in the loop.** The **Dashboard** shows non-zero KPIs. Open
**Matches**: the seeded guest *purple wallet* report is already matched to the found
wallet, with a similarity score. That score comes from the GenAI service (image →
attributes → embedding) and a pgvector nearest-neighbour search — GenAI drives the
workflow, it is not a bolt-on.

**4. Walk the flow live.**
- **New Intake** → log a found item (drop a photo, add notes, submit).
- Open the guest report page at http://localhost:3000/report/00000000-0000-0000-0000-000000000001, describe a lost item, add an email, submit.
- Back in **Matches**, the new candidate appears once matching runs; **Mailpit** (http://localhost:8025) captures any guest email.

**5. Observability.**

| Surface | URL | What to look at |
|---|---|---|
| Grafana | http://localhost:3030 (`admin`/`admin`) | *Services — RED* + *AMQP Consumers* dashboards |
| Prometheus | http://localhost:9090 | `/targets` (scrape health), `/alerts` (rules) |
| Swagger UI | http://localhost:8080/swagger-ui.html | all Spring APIs, aggregated |
| GenAI metrics | http://localhost:8000/metrics | provider latency / request counts |

### Where each graded requirement is demonstrated

| Requirement | Where to verify |
|---|---|
| Client-side app | http://localhost:3000 — login, dashboard, intake, matches |
| ≥3 Spring microservices | 8 services behind the gateway; listed in Swagger UI |
| Persistent database | per-service Postgres 17 (pgvector in matching-service) |
| Separate Python GenAI, in a real workflow | match scores on **Matches**; docs at http://localhost:8000/docs |
| Local Docker runtime, ≤3 commands | step 1 above |
| Kubernetes deployment | [`docs/deployment/local-kubernetes.md`](docs/deployment/local-kubernetes.md), `infra/helm/foundflow/`; live on AET at `team-chaos-monkeys.stud.k8s.aet.cit.tum.de` (TUM network) |
| CI/CD on GitHub Actions | `ci.yml` on every PR; `aet-helm-deploy.yml` runs `helm upgrade` on merge to `main` |
| Prometheus / Grafana observability | Grafana :3030, Prometheus :9090 |
| Automated tests | Gradle (services), pytest (genai), Vitest (client), PowerShell E2E |
| Architecture docs + UML | [`docs/architecture/`](docs/architecture/), [`docs/diagrams/`](docs/diagrams/) (`*.puml`) |
| OpenAPI / Swagger | http://localhost:8080/swagger-ui.html |
| No hardcoded credentials | `.env.example` (local); K8s `ConfigMap`/`Secret` from GitHub secrets |
| PR workflow + review | feature branches → PR into `development`; CI required |

> Want to start empty instead? Set `SEED_DEMO_DATA=false` in `.env` before `docker compose up`.

## Run locally

The full stack — frontend, gateway, seven Spring services, GenAI service, seven isolated Postgres databases, RabbitMQ, MinIO, Prometheus, and Grafana — boots from one Compose file. You need Docker Desktop (or any engine with Compose v2.24+) and roughly 6 GB of free RAM.

```bash
cp .env.example .env          # one-time, gitignored
# Set OPENAI_API_KEY in .env from the shared Bitwarden entry
docker compose up --build     # builds and starts everything
```

On a fresh machine, expect the first image build and startup to take roughly
**10-15 minutes**, depending on CPU, memory, and network speed. Subsequent starts
are significantly faster because Docker reuses downloaded images and build
layers. The frontend may become reachable before the backend services are ready;
wait until the login request succeeds before evaluating the application.

On first boot a demo venue, a staff account, three sample found items, and three matching guest lost reports are seeded automatically with photos (through the API, so real GenAI/pgvector matches form) — disable with `SEED_DEMO_DATA=false`. See the [Reviewer walkthrough](#reviewer-walkthrough) for a guided tour.

The default GenAI provider is OpenAI, so startup requires `OPENAI_API_KEY` but does not download Ollama or local models. Once `docker compose ps` shows the services running:

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

To stop and clean up volumes: `docker compose down -v --remove-orphans`.
The orphan cleanup also removes services that existed on the branch where the
stack was started but are absent after switching branches.

### Provider switch for GenAI

`GENAI_PROVIDER=openai` is the default and requires `OPENAI_API_KEY` from the shared Bitwarden entry. To run without an API key, set `GENAI_PROVIDER=local` and start the optional Ollama profile:

```bash
docker compose --profile ollama up --build
```

The first Ollama run downloads the configured models into the persistent `ollama-models` volume.

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

`.devcontainer/` defines a VS Code dev container with all three toolchains (Java 21, Node 22, Python 3.12) pre-installed. The container is the *toolbox*; the runtime stack is still launched separately with `docker compose up` against the host Docker socket. Ollama is available through the optional `ollama` Compose profile.

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
- **Releases:** `main` is the release branch; merging `development → main` triggers the AET deploy.
- **CI** (`.github/workflows/ci.yml`): runs on every PR — Gradle `check` for each backend service (matrix), pytest + ruff for `genai-service`, Vite build for the client, Orval drift check, and a full `docker compose up` + E2E test pass.
- **CD** (`.github/workflows/aet-helm-deploy.yml`): on every push to `main` (and via `workflow_dispatch`) it builds and pushes images to GHCR, then runs `helm upgrade --install` against the AET (Rancher RKE2) cluster — live at `team-chaos-monkeys.stud.k8s.aet.cit.tum.de`. `.github/workflows/azure-cycle.yml` provisions an ephemeral Azure VM, deploys, and destroys it on demand (`workflow_dispatch`). Charts live under `infra/helm/foundflow/`.

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

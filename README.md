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
on first boot, so a working match is visible once the intake and matching pipeline
has finished.

### Review it live on Rancher (recommended)

The app runs continuously on the course's AET (Rancher RKE2) cluster — nothing to build,
deployed by CD on every merge to `main`. This is the deployment to grade.

Open https://team-chaos-monkeys.stud.k8s.aet.cit.tum.de/
and log in with the same seeded accounts as local: staff `staff.demo@foundflow.local` /
`test12345`, admin `admin@foundflow.local` / `admin12345`. The same demo data (the *purple
wallet* match, the *Grand Plaza Hotel (Demo)* venue) is seeded, so steps **2–4** below read
identically — just against the live URL instead of `localhost`.

The stack is behind the cluster ingress, so the local port map does **not** apply. Reachable surfaces:

| Surface | AET (Rancher) URL |
|---|---|
| Staff app | `/` |
| Guest report | `/report/grand-plaza-hotel-demo` |
| Grafana | `/grafana` (admin login shared with the tutor on Artemis) |

Grafana is the public observability window on AET. Swagger UI, Prometheus, Mailpit, and the
GenAI `/metrics` endpoint aren't exposed through the ingress — to browse those, run the stack
locally with Compose below.

### Or run it locally with Docker Compose

**Prerequisites.** Docker Desktop (or any engine with Compose v2.24+) and roughly 6 GB of
free RAM.

> **⚠️ Required: the shared `.env` file.** The stack does **not** boot without it — the
> default GenAI provider is OpenAI and needs `OPENAI_API_KEY`. Drop the shared `.env` into
> the repo root; we shared a Google Drive link to a ready-to-use `.env` with all secret
> keys (incl. `OPENAI_API_KEY`) with the tutor in the Artemis group chat. No key at hand?
> Copy `.env.example` to `.env`, set `GENAI_PROVIDER=local`, and run the offline Ollama
> profile instead — see [Provider switch for GenAI](#provider-switch-for-genai).

**1. Start** (first boot ~10–15 min — wait until login succeeds):

```bash
docker compose up --build
```

Want to see it running on local Kubernetes instead? Follow
[`docs/deployment/local-kubernetes.md`](docs/deployment/local-kubernetes.md) — it walks
through the Helm quickstart and serves the app at http://foundflow.localtest.me/ with
Grafana at http://foundflow.localtest.me/grafana/.

**2. Log in** at http://localhost:3000 with the seeded staff account
`staff.demo@foundflow.local` / `test12345` and review the app from the normal
venue staff perspective. The admin account `admin@foundflow.local` / `admin12345`
is still available for user/admin checks.

**3. See it working — GenAI in the loop.** The **Dashboard** shows non-zero KPIs. Open
**Matches**: once the seeded intake events have been processed, the guest *purple
wallet* report is matched to the found wallet, with a similarity score. That score
comes from the GenAI service (image → attributes → embedding) and a pgvector
nearest-neighbour search — GenAI drives the workflow, it is not a bolt-on.

**4. Walk the flow live.**
- **New Intake** → log a third found item: download [`purple-shirt.jpg`](scripts/seed/assets/purple-shirt.jpg) (a purple cotton shirt), upload it, add notes, submit. It joins the two seeded found items.
- Open the guest report page at http://localhost:3000/report/grand-plaza-hotel-demo (the demo venue's name, slugified — guests normally reach this via a per-venue QR link), describe a lost item, add an email, submit.
- Back in **Matches**, the new candidate appears once matching runs.
- The candidate shows its similarity score and a `PENDING` status, with the pickup banner reflecting scheduling state (guest confirm/reject is not wired into the UI yet). **Mailpit** (http://localhost:8025) captures the match, pickup, and password-reset emails the system sends; following a pickup magic link opens the guest scheduling page at `/report/pickup/<token>`.

**5. Observability.**

| Surface | URL | What to look at |
|---|---|---|
| Grafana | http://localhost:3030 (`admin`/`admin`) | *Services — RED* + *AMQP Consumers* dashboards |
| Prometheus | http://localhost:9090 | `/targets` (scrape health), `/alerts` (rules) |
| Swagger UI | http://localhost:8080/api/swagger-ui.html | all Spring APIs, aggregated |
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
| Automated tests | Gradle (services), pytest (genai), Vitest (client), PowerShell E2E — run instructions under [Development](#development) |
| Architecture docs + UML | [`docs/architecture/`](docs/architecture/), [`docs/diagrams/`](docs/diagrams/) (`*.puml`) |
| OpenAPI / Swagger | http://localhost:8080/api/swagger-ui.html |
| No hardcoded credentials | `.env.example` (local); K8s `ConfigMap`/`Secret` from GitHub secrets |
| PR workflow + review | feature branches → PR into `development`; CI required |

> Want to start empty instead? Set `SEED_DEMO_DATA=false` in `.env` before `docker compose up`.

### Not yet wired

- **Guest match confirm/reject UI** — the `confirm`/`reject` endpoints and the public match-link API exist on the backend, but no guest-facing page drives them yet, so matches stay at `PENDING`.
- **Match lifecycle events** — `MatchConfirmed`, `NotificationSent`, and `CaseClosed` are described in the architecture docs but not yet implemented; outbound notifications today fire off `match-invite`, `pickup-confirmation`, and `password-reset` events only.
- **Domain metrics** — per-service RED metrics (rate/errors/duration) are live; the domain gauges (matches/min, GenAI extraction latency, vector-search latency) land incrementally.

## Run locally

Starting the stack and the demo flow are covered in the [Reviewer
walkthrough](#reviewer-walkthrough) above. This section is the reference for service
URLs, teardown, and the GenAI provider switch. The full stack — frontend, gateway,
seven Spring services, GenAI service, seven isolated Postgres databases, RabbitMQ,
MinIO, Prometheus, and Grafana — boots from the one `docker compose up --build`.

| URL | What it is |
|---|---|
| http://localhost:3000 | Frontend (React) |
| http://localhost:8080 | API gateway — single entry point for all backend calls |
| http://localhost:8080/api/swagger-ui.html | Aggregated OpenAPI UI for the Spring services, proxied through the gateway |
| http://localhost:8000/docs | `genai-service` FastAPI docs |
| http://localhost:8000/metrics | `genai-service` Prometheus scrape endpoint |
| http://localhost:9090 | Prometheus — scrape targets and alert rules (see [Observability](#observability)) |
| http://localhost:3030 | Grafana — Services — RED dashboard, default credentials `admin`/`admin` |
| http://localhost:8025 | Mailpit — captured outbound email (local/CI SMTP sink; nothing reaches the real Brevo account) |

The gateway is the only Spring service with a host port mapping — by design it is the sole public entry point for the other Spring services (`auth`, `lost-item`, `found-item`, `matching`, `pickup`, `notification`, `operations`), so their ports stay inside the Compose network.

To stop and clean up volumes: `docker compose down -v --remove-orphans`.
The orphan cleanup also removes services that existed on the branch where the
stack was started but are absent after switching branches.

### Provider switch for GenAI

`GENAI_PROVIDER=openai` is the default and uses the `OPENAI_API_KEY` already included in the shared `.env`. To run without an API key, set `GENAI_PROVIDER=local` and start the optional Ollama profile:

```bash
docker compose --profile ollama up --build
```

The first Ollama run downloads the configured models into the persistent `ollama-models` volume.

## Architecture

The system is **event-driven with synchronous edges**:

- **REST/JSON** carries user-facing commands and queries from the frontend, public magic-link flows for match confirmation and pickup scheduling, and synchronous calls to `genai-service` (attribute extraction, embedding, match verification).
- **Domain events** (`LostReportCreated`, `FoundItemCreated`, `MatchCandidateCreated`, `MatchInviteRequested`, …) carry async intake → matching → notification workflows. The bus is RabbitMQ — live in compose and wired for intake → matching and outbound notification requests (see [`docs/architecture/messaging-and-events.md`](docs/architecture/messaging-and-events.md)).

Each Spring service owns its own Postgres database; services never read each other's tables. Cross-service detail reads go through REST behind the gateway. Migrations are per-service Flyway. The `matching-service` database adds `pgvector` and stores embeddings produced by `genai-service`; the `pickup-service` database owns pickup schedules and booked pickups, while `notification-service` owns outbound delivery records.

Authentication is OAuth2 (authorization-code + PKCE) with JWT bearer tokens. `auth-service` issues tokens carrying `roles` and `venue_id` claims; downstream services enforce tenancy by venue. Role and endpoint matrix: [`docs/architecture/api-and-security.md`](docs/architecture/api-and-security.md).

Photo storage uses a shared abstraction so the same code targets MinIO locally and Azure Blob in the cloud — design rationale in [`docs/architecture/photo-storage.md`](docs/architecture/photo-storage.md).

## Repository layout

```
.
├── api/
│   └── openapi.yaml            — GenAI service contract
├── client/                     — React + Vite + TypeScript frontend
├── docs/                       — architecture/, course/, product/, deployment/, research/, diagrams/
├── services/
│   ├── gateway-service/        — Spring Cloud Gateway, edge routing
│   ├── auth-service/           — OAuth2 authorization server + user management
│   ├── lost-item-service/      — guest lost-item reports
│   ├── found-item-service/     — staff found-item intake
│   ├── matching-service/       — pgvector-backed match candidates
│   ├── pickup-service/         — pickup schedules, public pickup booking
│   ├── notification-service/   — outbound email delivery records
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
npm test                # Vitest unit/component tests (one-shot)
npm run test:coverage   # Vitest with v8 coverage (what CI runs)
npm run codegen         # regenerate Orval clients from the cached specs in client/openapi/
npm run codegen:fetch   # refresh client/openapi/*.json from the running gateway, then regenerate
```

Stack: React 19, Vite 8, TypeScript 6. The typed API client is generated by Orval from snapshots of each Spring service's springdoc output (cached under `client/openapi/`, refreshed via `codegen:fetch` against `http://localhost:8080/api/{prefix}/v3/api-docs`) — never hand-write request/response types Orval generates. CI's `codegen-check` job fails if the committed client drifts.

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

### End-to-end tests (`tests/e2e/`)

The PowerShell E2E suite runs against a running Compose stack — the same script CI executes:

```powershell
$env:GENAI_PROVIDER = "fake"   # deterministic provider, no API key needed
docker compose up --build -d
.\tests\e2e\foundflow-e2e.ps1
```

Parameters, covered scenarios, and triage notes: [`tests/e2e/README.md`](tests/e2e/README.md).

### Devcontainer

`.devcontainer/` defines a VS Code dev container with all three toolchains (Java 21, Node 22, Python 3.12) pre-installed. The container is the *toolbox*; the runtime stack is still launched separately with `docker compose up` against the host Docker socket. Ollama is available through the optional `ollama` Compose profile.

## API contract

The project runs a mixed contract model today:

- **GenAI service** — contract-first against `api/openapi.yaml`. `services/genai-service/app/api/schemas.py` mirrors the spec, and `services/genai-service/tests/golden/_contract.py` enforces alignment. Lint with `npx @redocly/cli lint api/openapi.yaml`; preview with `npx @redocly/cli preview-docs api/openapi.yaml` (config in `redocly.yaml`).
- **Spring services** — code-first via springdoc. Controllers are hand-written and `/v3/api-docs` is generated from the annotations; the gateway aggregates them at `http://localhost:8080/api/swagger-ui.html`.
- **Frontend** — Orval generates the typed React-Query client from snapshots of the Spring services' springdoc output cached under `client/openapi/`. Refresh and regenerate with `cd client && npm run codegen:fetch`.

## Per-developer Compose overrides

If a host port clashes with something else on your machine (e.g. another project already on `3000`), **don't edit `docker-compose.yml`** — copy [`docker-compose.override.yml.example`](docker-compose.override.yml.example) to `docker-compose.override.yml` (gitignored) and adjust there. Compose automatically merges that file with `docker-compose.yml` on every `docker compose up` — no flags, no env vars, no committed-file edits.

> Compose merges list-type fields (`ports`, `volumes`) by appending, not replacing. To swap a published port cleanly, use the `!override` tag — the example file shows the pattern. Requires Compose v2.24+.

## CI/CD and branching

- **Branches:** `feature/*`, `chore/*`, `fix/*` cut from `development`. PRs target `development`; merge via pull request only.
- **Releases:** `main` is the release branch; merging `development → main` triggers the AET deploy.
- **CI** (`.github/workflows/ci.yml`): runs on every PR — gitleaks secret scan, Gradle `check` for each backend service (matrix), pytest + ruff for `genai-service`, Vite build for the client, Orval drift check, and a full `docker compose up` + E2E test pass.
- **CD** (`.github/workflows/aet-helm-deploy.yml`): on every push to `main` (and via `workflow_dispatch`) it builds and pushes images to GHCR, then runs `helm upgrade --install` against the AET (Rancher RKE2) cluster — live at `team-chaos-monkeys.stud.k8s.aet.cit.tum.de`. `.github/workflows/azure-cycle.yml` provisions an ephemeral Azure VM, deploys, and destroys it on demand (`workflow_dispatch`). Charts live under `infra/helm/foundflow/`.

### Secret scanning

[gitleaks](https://github.com/gitleaks/gitleaks) guards the "no hardcoded credentials" rule at two layers: a CI job scans the full git history on every PR, and a pre-commit hook catches secrets locally before they are ever committed. Install the hook once per clone:

```sh
brew install pre-commit   # or: pipx install pre-commit
pre-commit install
```

Known-safe placeholders (`.env.example`, frontend test fixtures) are allowlisted in [`.gitleaks.toml`](.gitleaks.toml). If the scan flags something that genuinely isn't a secret, extend the allowlist there — never commit the real thing "just for now".

## Observability

`docker compose up` brings up a Prometheus + Grafana stack that scrapes every FoundFlow service out of the box:

| URL | What it is |
|---|---|
| http://localhost:9090 | Prometheus — `/targets` for scrape health, `/alerts` for the configured rules (`ServiceDown`, `HighErrorRate`) |
| http://localhost:3030 | Grafana — log in with `admin`/`admin` (override via `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` in `.env`). The provisioned **Services — RED** dashboard is the default view. |

Every Spring service exposes `/actuator/prometheus` (Micrometer); `genai-service` exposes `/metrics` (`prometheus_client` + `prometheus-fastapi-instrumentator`). Scrape config lives under `infra/prometheus/`; provisioned dashboards under `infra/grafana/dashboards/`. Per-service RED metrics are wired today; domain metrics (matches/min, GenAI extraction latency, vector search latency) are added incrementally as features ship.

## Documentation index

- [`docs/README.md`](docs/README.md) — documentation index
- [`docs/product/application-overview.md`](docs/product/application-overview.md) — user-facing workflows and service responsibilities
- [`docs/product/problem-statement.md`](docs/product/problem-statement.md) — domain framing
- [`docs/architecture/system-architecture.md`](docs/architecture/system-architecture.md) — system design (diagrams under `docs/diagrams/`)
- [`docs/architecture/api-and-security.md`](docs/architecture/api-and-security.md) — roles, claims, endpoint permissions
- [`docs/architecture/messaging-and-events.md`](docs/architecture/messaging-and-events.md) — RabbitMQ routing and event payloads
- [`docs/architecture/photo-storage.md`](docs/architecture/photo-storage.md) — storage abstraction shared by lost/found item services
- [`docs/deployment/local-kubernetes.md`](docs/deployment/local-kubernetes.md) — local Helm/Kubernetes runtime
- [`infra/helm/foundflow/README.md`](infra/helm/foundflow/README.md) — Helm chart structure and AET deployment notes
- [`docs/course/requirements.md`](docs/course/requirements.md) — course requirements & grading

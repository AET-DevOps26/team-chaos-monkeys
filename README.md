# Team Chaos Monkeys — FoundFlow

Repository for the DevOps course project (CIT423001) at TUM, summer term 2026.

FoundFlow is a cloud-native lost-and-found platform for hospitality and event venues. See [`docs/problem-statement.md`](docs/problem-statement.md) for the problem framing and [`docs/architecture.md`](docs/architecture.md) for the system overview and UML diagrams.

## Team

| Name | Subsystem |
|---|---|
| Luca Kollmer | GenAI service |
| Arthur Gersbacher | Frontend |
| Johannes Kirchner | Backend (Spring Boot services) |

Subsystem ownership defines who is primarily responsible for design, implementation, and the individual oral examination at the end of the term. Cross-subsystem collaboration on integration, CI/CD, and observability is expected and tracked through pull requests and reviews.

## Repository Layout

```
.
├── README.md                  — this file
├── client/                    — React frontend application
├── docs/
│   ├── problem-statement.md   — Deliverable: Problem Statement (08.05.2026)
│   ├── architecture.md        — Deliverable: System Overview / Architecture (08.05.2026)
│   ├── task-description.md    — original course brief (do not edit)
│   └── assets/                — diagrams and documentation images
└── services/
    └── auth-service/           — initial Spring Boot authentication service
```

Additional service directories, shared API contracts (`api/`), and infrastructure definitions (`infra/`) will be added as implementation work progresses.

## Observability

After `docker compose up`, Prometheus scrapes every FoundFlow service and Grafana ships a provisioned **Services — RED** dashboard:

- Prometheus: `http://localhost:9090` — see `/targets` for scrape health and `/alerts` for the configured rules (`ServiceDown`, `HighErrorRate`).
- Grafana: `http://localhost:3030` — log in with `admin`/`admin` (override via `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` in `.env`). The Services — RED dashboard is the default starting view.

## Frontend routes

- `/report` - public page for submitting a lost-item report (description, when it was lost, contact email, optional photo). Submits to the `lost-item-service` via the generated API client.

## Local development overrides

If a host port clashes with something else on your machine (e.g. another project running on `3000`), or you want to mount a debug volume into one service, **don't edit `docker-compose.yml`** — copy [`docker-compose.override.yml.example`](docker-compose.override.yml.example) to `docker-compose.override.yml` (gitignored) and adjust it. Compose automatically merges that file with `docker-compose.yml` whenever you run `docker compose up`. No flags, no env vars, no committed-file edits.

This keeps the committed compose readable as the canonical source of truth while letting each developer customize their local stack independently.

> **Heads-up:** Compose merges list-type fields (`ports`, `volumes`, etc.) by appending, not replacing. To swap a published port cleanly, use the `!override` tag — the example file shows the pattern. Requires Compose v2.24+.

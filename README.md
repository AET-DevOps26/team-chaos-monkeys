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
├── docs/
│   ├── problem-statement.md   — Deliverable: Problem Statement (08.05.2026)
│   └── architecture.md        — Deliverable: System Overview / Architecture (08.05.2026)
└── task-description.md        — original course brief (do not edit)
```

Service directories (`services/intake-service`, `services/matching-service`, `services/notification-service`, `services/genai-service`, `web-client`) and infrastructure directories (`infra/`, `api/`) will be added once implementation begins.

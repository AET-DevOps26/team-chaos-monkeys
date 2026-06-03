# Project Requirements

This document summarizes the project requirements from the original course task
description.

## Required System

FoundFlow must be a complete DevOps-oriented web application with:

- Client-side application
- Spring Boot server side with at least three microservices
- Persistent database
- Separate Python GenAI component
- Local Docker-based runtime
- Kubernetes deployment
- CI/CD with GitHub Actions
- Prometheus/Grafana observability
- Automated tests
- Architecture documentation and UML-style diagrams
- OpenAPI/Swagger API documentation

## Engineering Expectations

- Monorepo with client, services, GenAI, deployment files, and docs.
- Pull-request based workflow with review.
- CI on PRs.
- Deployment automation on merge to `main`.
- Reproducible local setup in a small number of commands.
- Environment-specific configuration through environment variables and secrets.
- No hardcoded credentials.

## Grading and Evaluation

Project grading is split across team and individual evaluation:

| Part | Weight | Notes |
| --- | --- | --- |
| Aggregated team grade | 40% | Three tutor checkpoints across the semester. |
| Team final presentation | 30% | Final architecture explanation and live demo. |
| Individual oral examination | 30% | Each student explains one personally developed artefact. |

For the individual oral examination, each student should be able to explain:

- Their subsystem and its role in the overall system.
- The technical decisions they made.
- How their component integrates with other services and infrastructure.

For checkpoints and final evaluation, deployment stability matters: the system
should be reachable, runnable end to end, and documented well enough that tutors
do not depend on local-only knowledge.

## Required Diagrams

The required UML-style diagrams are maintained as PlantUML source:

- [Class diagram](../diagrams/class-diagram.puml)
- [Use case diagram](../diagrams/use-case-diagram.puml)
- [Component diagram](../diagrams/component-diagram.puml)

Additional architecture detail:

- [Service communication diagram](../diagrams/service-communication-diagram.puml)

## Current Implementation Coverage

| Requirement | Implementation |
| --- | --- |
| Client | `client` React/Vite app |
| Spring microservices | Gateway plus auth, lost, found, matching, notification, operations, pickup |
| GenAI | `services/genai-service` FastAPI service |
| Database | Per-service PostgreSQL, pgvector for matching |
| Local runtime | `docker-compose.yml` |
| Kubernetes | `infra/helm/foundflow` |
| Observability | `infra/prometheus`, `infra/grafana`, Helm dashboards/rules |
| API docs | Service Swagger docs aggregated through gateway |
| Tests | Java service tests, GenAI pytest suite, client Vitest tests, E2E scripts |

## Common Failure Modes to Avoid

- Treating the project as a feature checklist while deployment remains fragile.
- Integrating services only at the end.
- Keeping CI/CD superficial or disconnected from real service tests.
- Adding GenAI as a decoration rather than as part of a user-facing workflow.
- Deferring documentation until after the architecture has already drifted.

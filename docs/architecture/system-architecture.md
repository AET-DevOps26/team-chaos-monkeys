# System Architecture

FoundFlow is a microservice-based monorepo. The system is split into a React
client, a Spring Cloud Gateway, seven Spring Boot business services, a Python
GenAI service, shared Java libraries, and deployment/observability
infrastructure.

## Runtime Shape

| Layer | Technology |
| --- | --- |
| Client | React, Vite, TypeScript |
| Gateway | Spring Cloud Gateway |
| Backend services | Spring Boot 4.0.6, Java 21 |
| GenAI | Python 3.12, FastAPI |
| Databases | PostgreSQL 17, pgvector for matching |
| Events | RabbitMQ topic exchange |
| Photo storage | MinIO locally, Azure Blob-compatible adapter for cloud |
| Deployment | Docker Compose locally, Helm/Kubernetes for cluster deployment |
| Observability | Prometheus, Grafana, Spring Actuator, Micrometer |

## Service Ownership

Each Spring business service owns its database. Services do not read each
other's tables directly. Cross-service state is exchanged through REST APIs or
RabbitMQ events.

| Service | Owns | Communicates with |
| --- | --- | --- |
| `gateway-service` | External route table and Swagger aggregation | Routes to all API services |
| `auth-service` | `users`, `refresh_tokens`, JWT signing keys/config | Frontend, gateway, resource services through JWKs |
| `lost-item-service` | `lost_reports`, lost-report mark collections, lost photo keys | Publishes RabbitMQ intake events; calls GenAI synchronously over REST for extraction; uses MinIO/Azure photo storage |
| `found-item-service` | `found_items`, found-item mark collections, found photo keys | Publishes RabbitMQ intake events; calls GenAI synchronously over REST for extraction; uses MinIO/Azure photo storage |
| `matching-service` | `matches`, `item_embeddings`, `match_email_logs` | Consumes RabbitMQ intake events and publishes match-candidate events; calls GenAI synchronously over REST for embeddings/verification; calls lost/found services over REST for referenced item reads |
| `pickup-service` | `pickups`, `pickup_schedules`, `pickup_email_logs` | Public/staff REST APIs, shared magic-link library |
| `notification-service` | `notifications` | REST API today; planned RabbitMQ consumer for outbound notification requests |
| `operations-service` | `venues` | Calls found/lost/matching count endpoints for KPIs |
| `genai-service` | No persistent domain data | OpenAI or Ollama provider APIs |

## Communication

### External HTTP

The browser talks to the gateway on port `8080` in local compose. The gateway
routes:

| External path | Target |
| --- | --- |
| `/api/auth/**`, `/api/users/**` | `auth-service:8081` |
| `/api/lost-items/**`, `/api/lost-reports/**` | `lost-item-service:8082` |
| `/api/found-items/**` | `found-item-service:8083` |
| `/api/matches/**` | `matching-service:8084` |
| `/api/notifications/**` | `notification-service:8085` |
| `/api/venues/**` | `operations-service:8086` |
| `/api/pickups/**` | `pickup-service:8087` |

Downstream services still enforce their own JWT/resource authorization.
Bypassing the gateway does not bypass service security.

### Internal REST

- Lost/found services call `genai-service` synchronously for best-effort
  attribute extraction during intake.
- Matching calls `genai-service` synchronously for embeddings and match
  verification while processing candidates. The trigger for that work is often
  a RabbitMQ intake event, but the GenAI call itself is REST.
- Matching loads referenced lost/found resources through REST clients when
  validating or enriching matches.
- Operations calls count endpoints in found/lost/matching services for KPIs and
  forwards the caller's bearer token.

`/api/lost-items` is the canonical lost-report API path. `/api/lost-reports`
is a legacy compatibility route currently still supported by the service and
gateway.

### Events

RabbitMQ carries domain events from intake services to matching:

- `lost-report.created.v1`
- `lost-report.updated.v1`
- `found-item.logged.v1`
- `found-item.updated.v1`
- `match-candidate.created.v1`

Notification messaging is planned as a separate RabbitMQ channel for outbound
notification requests. See [messaging-and-events.md](messaging-and-events.md)
for the planned notification exchange and ownership rules.

See [messaging-and-events.md](messaging-and-events.md) for payload ownership
and routing.

## Shared Modules

| Module | Purpose |
| --- | --- |
| `shared/common-domain` | Shared embeddable `ItemAttributes` value object. |
| `shared/domain-events` | Versioned RabbitMQ event payloads and routing constants. |
| `shared/genai-client` | Spring client/autoconfiguration for GenAI calls and extraction fallback. |
| `shared/magic-link` | HMAC-scoped public tokens for match and pickup flows. |
| `shared/photo-storage` | Storage interface, MinIO/Azure/local adapters, constraints, signed URL response. |
| `shared/test-architecture` | ArchUnit suites used by service architecture tests. |

## Data and Infrastructure

- Every Spring service has a dedicated PostgreSQL database and Flyway history.
- Matching uses `pgvector/pgvector:pg17` for `item_embeddings`.
- MinIO stores item photos in local compose. Services persist only `photoKey`.
- RabbitMQ queues are durable; PostgreSQL remains authoritative.
- Prometheus scrapes Spring actuator endpoints and the GenAI `/metrics`
  endpoint. Grafana dashboards are committed under `infra/grafana` and
  `infra/helm/foundflow/dashboards`.

## Main Workflows

### Lost Report Intake

1. Guest submits `/api/lost-items`.
2. Lost service optionally stores the uploaded photo.
3. Lost service calls GenAI extraction best-effort.
4. Lost service persists `LostReport`.
5. Lost service publishes `lost-report.created.v1`.
6. Matching consumes the event and updates candidate matches.

### Found Item Intake

1. Staff submits `/api/found-items` with a required photo.
2. Found service stores the photo and extracts attributes best-effort.
3. Found service persists `FoundItem`.
4. Found service publishes `found-item.logged.v1`.
5. Matching consumes the event, creates embeddings, and searches lost reports.

### Match Confirmation and Pickup

1. Staff creates a public match link from `/api/matches/{id}/public-link`.
2. Guest opens the link and confirms or rejects the match.
3. Confirmed match links can be used to list pickup slots.
4. Guest schedules pickup through the pickup service.
5. Pickup service creates a `pickup_manage` token for reschedule/cancel flows.

## Diagrams

Open the PlantUML source files to view or render the diagrams:

- [Component diagram](../diagrams/component-diagram.puml) shows the top-level
  runtime building blocks.
- [Service communication diagram](../diagrams/service-communication-diagram.puml)
  shows the detailed REST, RabbitMQ, GenAI, photo-storage, and planned
  notification flows.
- [Use case diagram](../diagrams/use-case-diagram.puml)
- [Class diagram](../diagrams/class-diagram.puml)

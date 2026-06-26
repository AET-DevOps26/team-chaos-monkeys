# Application Overview

FoundFlow is a lost-and-found platform for venues such as hotels, clubs,
event locations, museums, universities, and transit hubs. It replaces ad-hoc
spreadsheets and email chains with a service-oriented workflow for reporting,
logging, matching, notifying, and handing over lost items.

## High-Level Flow

1. Guests report lost items through a public React form at `/report`.
2. Staff log found items through the authenticated staff app.
3. Lost and found intake services store their own records and photos.
4. Intake events are published to RabbitMQ.
5. The matching service consumes intake events, creates embeddings through the
   GenAI service, searches candidates with pgvector, and persists matches.
6. Staff can create a public match link for a candidate match.
7. Guests confirm or reject a match through the magic link.
8. Confirmed matches can be scheduled for pickup through the pickup service.

The frontend is split across two SPAs. The authenticated staff `client` (`/`)
serves the staff flows; the unauthenticated `public-report-client` (`/report`)
serves the guest report flow. The edge/ingress routes each prefix to the right
app:

- `/login`: staff login (client)
- `/`: found-item intake (client)
- `/found-items`: found-item overview (client)
- `/lost-items`: lost-report overview (client)
- `/report`: public lost-item report form (public-report-client)
- `/report/confirmation`: report confirmation page (public-report-client)

## Users

| User | Needs | Primary entry point |
| --- | --- | --- |
| Guest | Report a lost item, view a candidate match, confirm/reject, schedule pickup | Public report and magic links |
| Venue Staff | Log found items, manage found/lost lists, create match links, manage pickups | Authenticated staff app |
| Operations Manager | Manage venue-level operations and KPIs for their venue | Authenticated API/UI surface |
| Admin | Cross-venue administration and user management | Authenticated API/UI surface |

## Service Responsibilities

| Service | Responsibility |
| --- | --- |
| `client` | Authenticated staff React/Vite SPA served as static assets at `/`. Consumes generated TypeScript API clients. |
| `public-report-client` | Unauthenticated guest React/Vite SPA served at `/report` for the public lost-item report form. Its own deployable micro-frontend, scoped to the lost-items API. |
| `edge` | nginx front door (compose only) mirroring the k8s ingress: routes `/` → `client`, `/report` → `public-report-client`, `/api` → `gateway-service`. |
| `gateway-service` | Single external API entrypoint for `/api/**`, actuator proxy routes, and Swagger aggregation. |
| `auth-service` | Users, roles, password login, refresh tokens, JWT issuing, JWK publishing. |
| `lost-item-service` | Public lost-report intake, lost-report CRUD, optional lost-report photos, lost-report events. |
| `found-item-service` | Staff found-item intake, found-item CRUD, required found-item photos, found-item events. |
| `matching-service` | Candidate matching, match status, vector index, match verification, public match links, match email log. |
| `pickup-service` | Pickup schedules, booked pickups, public pickup magic-link flow, and pickup-confirmation event publishing. |
| `notification-service` | RabbitMQ consumers for match invites, pickup confirmations, and password resets; persists notification delivery records. |
| `operations-service` | Venues and venue KPIs, including downstream count aggregation. |
| `genai-service` | Stateless Python/FastAPI service for attribute extraction, embeddings, and match verification. |

## GenAI Integration

The GenAI service is not a decorative sidecar. It contributes directly to the
matching workflow:

- `/extract-attributes` turns descriptions and optional images into structured
  item attributes.
- `/embed` creates vectors for lost reports, found items, and search queries.
- `/verify-match` produces a verdict, confidence, rationale, and model info for
  a candidate lost/found pair.

Provider selection is configuration-driven via `GENAI_PROVIDER=openai|local|fake`.
OpenAI is used for cloud-backed runs, Ollama for local LLM runs, and `fake` for
deterministic tests.

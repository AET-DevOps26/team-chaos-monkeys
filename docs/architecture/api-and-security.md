# API Contract and Security

FoundFlow uses the Auth Service as JWT issuer. Business services are OAuth2
resource servers and validate bearer tokens against the Auth Service JWK set.

## Security Model

Default resource-server configuration:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://auth-service:8081/oauth2/jwks
```

Access tokens include:

```json
{
  "iss": "http://localhost:8081",
  "roles": ["STAFF"],
  "venue_id": "00000000-0000-0000-0000-000000000000"
}
```

Spring maps `roles` to `ROLE_*` authorities.

## Roles

| Role | Scope |
| --- | --- |
| `ADMIN` | Cross-venue access and admin user management |
| `OPS_MANAGER` | Own-venue access and non-admin user management |
| `STAFF` | Own-venue operational access |

## Public Endpoints

The following endpoint groups are public:

- `GET /actuator/health`
- Swagger/OpenAPI endpoints exposed by services and aggregated by the gateway
- `POST /api/lost-items`
- Public match magic-link endpoints under `/api/matches/public/**`
- Public pickup magic-link endpoints under `/api/pickups/public/**`

Public magic-link endpoints validate HMAC-signed tokens with scoped token
types, currently `match_view` and `pickup_manage`, and a seven-day default TTL.

## API Documentation Sources

FoundFlow currently has two OpenAPI documentation flows:

| Source | Purpose | Consumers |
| --- | --- | --- |
| `api/openapi.yaml` | Contract-first GenAI API | Redocly, `shared/genai-client`, GenAI contract checks |
| Spring service `/v3/api-docs` output | Code-first Spring service APIs | Gateway Swagger UI, `client/openapi/*.json`, Orval-generated frontend client |

Generated Spring snapshots stay under `client/openapi/` because they are inputs
for frontend code generation. The `api/` directory is reserved for manually
owned API contracts, currently only the GenAI synchronous API.

## Venue Authorization

Resource-level authorization is based on `venueId`.

| Role | Rule |
| --- | --- |
| `ADMIN` | Can access resources across venues |
| `STAFF` | Can access resources whose `venueId` equals JWT `venue_id` |
| `OPS_MANAGER` | Can access resources whose `venueId` equals JWT `venue_id` |

Create behavior:

- Admins may set `venueId` in requests.
- Staff and ops managers receive `venueId` from the JWT; conflicting request
  values are ignored or rejected depending on endpoint semantics.
- Public lost-report creation requires a `venueId` in the request.

Update/read/delete behavior:

1. Load the existing resource.
2. Read its `venueId`.
3. Check access against JWT role and `venue_id`.
4. Apply the operation only after authorization succeeds.

Expected failures:

| Situation | Response |
| --- | --- |
| Missing or invalid JWT | `401 Unauthorized` |
| Wrong role or wrong venue | `403 Forbidden` |
| Authorized but missing resource | `404 Not Found` |

## Endpoint Ownership

| Base path | Owner | Notes |
| --- | --- | --- |
| `/api/auth` | `auth-service` | Login, refresh, logout |
| `/api/users` | `auth-service` | Admin/ops user management |
| `/api/lost-items` | `lost-item-service` | Canonical lost-report API |
| `/api/found-items` | `found-item-service` | Found-item intake and management |
| `/api/matches` | `matching-service` | Match CRUD, public match links, match email log |
| `/api/pickups` | `pickup-service` | Pickup schedules, pickups, public pickup links |
| `/api/notifications` | `notification-service` | Notification records and blueprint placeholders |
| `/api/venues` | `operations-service` | Venues and KPIs |

## Service-Specific Authorization Rules

These notes cover only authorization behavior that differs from the global
venue rules above. API-shape details such as multipart upload formats and photo
constraints are documented in [photo-storage.md](photo-storage.md) and the
service Swagger/OpenAPI docs.

- Lost/found photo endpoints first authorize access to the owning lost report or
  found item. Services then use the `photoKey` stored on that record; callers do
  not provide arbitrary storage keys.
- Matching create/update operations load the referenced lost report and found
  item, require both to belong to the same `venueId`, and then apply the
  caller's venue access to that venue.
- Public match and pickup endpoints authorize through scoped magic-link tokens
  instead of JWTs. Staff-facing match and pickup endpoints still use JWT role
  and venue checks.
- Operations KPI endpoints forward the caller's bearer token to downstream
  found/lost/matching count endpoints, so downstream service authorization still
  applies.

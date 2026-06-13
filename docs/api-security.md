# API Endpoints and Security Requirements

This document describes the current FoundFlow HTTP API contract and the security rules enforced by the services.

## Global Security Model

All business services are OAuth2 resource servers and validate incoming bearer JWTs against the Auth Service JWK set.

Required JWT configuration in the business services:

```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost:8081
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://auth-service:8081/oauth2/jwks
```

Required JWT claims:

```json
{
  "iss": "http://localhost:8081",
  "roles": ["STAFF"],
  "venue_id": "00000000-0000-0000-0000-000000000000"
}
```

Spring Security maps the custom `roles` claim to `ROLE_*` authorities. For example, `STAFF` becomes `ROLE_STAFF`.

Open endpoints in the business services:

| Endpoint | Access |
| --- | --- |
| `GET /actuator/health` | Public |
| `GET /swagger-ui/**` | Public |
| `GET /swagger-ui.html` | Public |
| `GET /v3/api-docs/**` | Public |

Public magic-link endpoints are also unauthenticated. They authorize by validating an HMAC-signed token with a seven-day expiry and a scoped token type.

All `api/**` endpoints in the business services require an authenticated JWT with one of the roles below, except for the public lost-item report creation endpoint:

| Method | Endpoint | Access |
| --- | --- | --- |
| `POST` | `/api/lost-items` | Public |
| `POST` | `/api/lost-reports` | Public compatibility path |
| `GET` | `/api/matches/public/{token}` | Public magic link; view one match |
| `PUT` | `/api/matches/public/match-links/{token}/confirm` | Public magic link; confirm one match |
| `PUT` | `/api/matches/public/match-links/{token}/reject` | Public magic link; reject one match |
| `GET` | `/api/pickups/public/{token}` | Public magic link; list available pickup slots |
| `POST` | `/api/pickups/public/{token}` | Public magic link; schedule pickup and create a pickup-management link |
| `PUT` | `/api/pickups/public/{token}` | Public pickup-management magic link; reschedule pickup |
| `DELETE` | `/api/pickups/public/{token}` | Public pickup-management magic link; cancel pickup |

Protected endpoints require one of:

| Role | Meaning |
| --- | --- |
| `ADMIN` | Cross-venue access |
| `STAFF` | Own-venue access only |
| `OPS_MANAGER` | Own-venue access only |

Additional Auth Service rule:

| Role | User-management scope |
| --- | --- |
| `ADMIN` | Can manage users across all venues, including admins |
| `OPS_MANAGER` | Can manage non-admin users in its own venue only |
| `STAFF` | Cannot manage users |

Expected error behavior:

| Situation | Response |
| --- | --- |
| Missing or invalid JWT | `401 Unauthorized` |
| Authenticated but wrong role or wrong venue | `403 Forbidden` |
| Authenticated and allowed, but resource does not exist | `404 Not Found` |

## Venue Access Rules

Resource-level authorization is based on `venueId`.

| Role | Rule |
| --- | --- |
| `ADMIN` | Can access resources across all venues |
| `STAFF` | Can access only resources whose `venueId` equals JWT `venue_id` |
| `OPS_MANAGER` | Can access only resources whose `venueId` equals JWT `venue_id` |

Create behavior:

| Role | Rule |
| --- | --- |
| `ADMIN` | May set `venueId` from the request |
| `STAFF` / `OPS_MANAGER` | Request `venueId` is ignored; `venueId` is taken from JWT `venue_id` |
| Public lost-item reporter | Must provide `venueId` in the request |

Update/delete/read behavior:

1. Load the existing resource.
2. Read the resource `venueId`.
3. Check access against the JWT.
4. Apply the operation only after the venue check succeeds.

List behavior:

| Role | Rule |
| --- | --- |
| `ADMIN` | Lists all matching resources |
| `STAFF` / `OPS_MANAGER` | Lists only resources for JWT `venue_id` |

Count and histogram behavior:

| Role | Rule |
| --- | --- |
| `ADMIN` | May optionally pass `venueId` to count/histogram endpoints to scope KPI data to one venue |
| `STAFF` / `OPS_MANAGER` | Count/histogram endpoints are always scoped to JWT `venue_id`; passing another `venueId` returns `403 Forbidden` |

## Auth Service

Base service responsibility: users, roles, JWT issuing and JWK publishing.

Local Docker Compose exposes the Auth Service on `http://localhost:8081` because the JWT issuer is configured as `http://localhost:8081` and browser-based OAuth redirects use localhost during development.

### User API

`/api/users/**` endpoints require `ADMIN` or `OPS_MANAGER`.

Venue rules for user management:

| Role | Rule |
| --- | --- |
| `ADMIN` | Can create, list, read, update and delete users across all venues |
| `OPS_MANAGER` | Can create, list, read, update and delete users only in JWT `venue_id` |
| `OPS_MANAGER` | Cannot create or promote `ADMIN` users |
| `OPS_MANAGER` | Cannot delete itself |
| `OPS_MANAGER` | Request `venueId` is ignored on create; the created user receives JWT `venue_id` |

`UserResponse` includes `venueId` so clients can display and audit venue assignment:

```json
{
  "id": "00000000-0000-0000-0000-000000000000",
  "email": "staff@example.com",
  "role": "STAFF",
  "venueId": "11111111-1111-1111-1111-111111111111"
}
```

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/users` | - | `ADMIN` global; `OPS_MANAGER` own venue only, non-admin roles only |
| `GET` | `/api/users` | - | `ADMIN` sees all; `OPS_MANAGER` sees own venue only |
| `GET` | `/api/users/by-email` | `email` | `ADMIN` global; `OPS_MANAGER` own venue only |
| `GET` | `/api/users/{id}` | - | `ADMIN` global; `OPS_MANAGER` own venue only |
| `PUT` | `/api/users/{id}` | - | `ADMIN` global; `OPS_MANAGER` own venue only, cannot promote to admin |
| `DELETE` | `/api/users/{id}` | - | `ADMIN` global; `OPS_MANAGER` own venue only and cannot delete itself |

### Authorization Server Endpoints

The Auth Service also exposes the Spring Authorization Server endpoints, including token and JWK endpoints. The relevant JWK URL for internal services is:

| Method | Endpoint | Access |
| --- | --- | --- |
| `GET` | `/oauth2/jwks` | Used by resource servers for JWT signature validation |

Access-token JWTs include:

| Claim | Source |
| --- | --- |
| `roles` | Authenticated user's role, without `ROLE_` prefix |
| `venue_id` | Authenticated user's assigned venue, when present |
| `iss` | `http://localhost:8081` |

## Found Item Service

Base path: `/api/found-items`

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/found-items` | `multipart/form-data` with `request` JSON and required `photo` file | `ADMIN`, `STAFF`, `OPS_MANAGER`; venue rules apply; service stores the photo and persists only the generated `photoKey` |
| `GET` | `/api/found-items` | optional `status` | `ADMIN` sees all; staff/ops own venue only |
| `GET` | `/api/found-items/count` | optional `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/found-items/histogram` | optional `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/found-items/{id}` | - | Resource venue check |
| `PUT` | `/api/found-items/{id}` | - | Resource venue check |
| `PUT` | `/api/found-items/{id}/photo` | `multipart/form-data` `photo` file | Resource venue check; replaces `photoKey` from storage output only |
| `GET` | `/api/found-items/{id}/photo` | - | Resource venue check; streams the stored photo |
| `GET` | `/api/found-items/{id}/photo-url` | - | Resource venue check; returns a short-lived browser-reachable signed URL for the photo (MinIO/Azure). The local-filesystem provider returns `501 Not Implemented`; callers can use the `/photo` proxy endpoint instead |
| `DELETE` | `/api/found-items/{id}` | - | Resource venue check |

Counts and histogram day buckets are aggregated in the database. Week and month buckets are derived from the daily buckets.

Count response:

```json
{
  "count": 23
}
```

Histogram response:

```json
{
  "perDay": [
    { "bucketStart": "2026-05-19", "count": 23 }
  ],
  "perWeek": [
    { "bucketStart": "2026-05-18", "count": 23 }
  ],
  "perMonth": [
    { "bucketStart": "2026-05-01", "count": 23 }
  ]
}
```

Histogram buckets include only buckets that exist in the filtered data. Empty dates/weeks/months are not zero-filled.

## Lost Item Service

Canonical base path: `/api/lost-items`

Compatibility base path currently also supported: `/api/lost-reports`

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/lost-items` | - | Public; `venueId` is required in the request. Authenticated staff/ops still use JWT `venue_id` |
| `POST` | `/api/lost-items` | `multipart/form-data` with `request` JSON and optional `photo` file | Same as JSON create; service stores the photo and persists only the generated `photoKey` |
| `GET` | `/api/lost-items` | optional `status` | `ADMIN` sees all; staff/ops own venue only |
| `GET` | `/api/lost-items/count` | optional `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/lost-items/histogram` | optional `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/lost-items/{id}` | - | Resource venue check |
| `PUT` | `/api/lost-items/{id}` | - | Resource venue check |
| `PUT` | `/api/lost-items/{id}/photo` | `multipart/form-data` `photo` file | Resource venue check; replaces `photoKey` from storage output only |
| `GET` | `/api/lost-items/{id}/photo` | - | Resource venue check; streams the stored photo |
| `GET` | `/api/lost-items/{id}/photo-url` | - | Resource venue check; returns a short-lived browser-reachable signed URL for the photo (MinIO/Azure). The local-filesystem provider returns `501 Not Implemented`; callers can use the `/photo` proxy endpoint instead |

Count and histogram responses use the same shape as the Found Item Service.

Histogram buckets include only buckets that exist in the filtered data. Empty dates/weeks/months are not zero-filled.

## Matching Service

Base path: `/api/matches`

Match statuses:

| Status |
| --- |
| `PENDING` |
| `CONFIRMED` |
| `REJECTED` |

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/matches` | - | `ADMIN`, `STAFF`, `OPS_MANAGER`; venue rules apply |
| `GET` | `/api/matches` | optional `foundItem`, `lostItem`, `status` | `ADMIN` sees all; staff/ops own venue only |
| `GET` | `/api/matches/count` | optional `foundItem`, `lostItem`, `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/matches/histogram` | optional `foundItem`, `lostItem`, `status`, optional `venueId` for admins | Same as list |
| `GET` | `/api/matches/{id}` | - | Resource venue check |
| `PUT` | `/api/matches/{id}` | - | Resource venue check |
| `POST` | `/api/matches/{id}/public-link` | optional recipient email | Resource venue check; creates a seven-day public match magic link and stores a local email-log entry. If omitted, the lost report's stored contact email is used. Existing match invites are returned instead of re-sent. |
| `GET` | `/api/matches/public-link-email-log` | optional `recipient` | Staff/ops see own venue only; admins see all; local/test outbox for the first magic-link email |
| `GET` | `/api/matches/public/{token}` | - | Public magic link scoped to one match |
| `PUT` | `/api/matches/public/match-links/{token}/confirm` | - | Public magic link scoped to one match; sets status to `CONFIRMED` |
| `PUT` | `/api/matches/public/match-links/{token}/reject` | - | Public magic link scoped to one match; sets status to `REJECTED` |

Matching filters can be combined freely. Examples:

```text
GET /api/matches?status=PENDING
GET /api/matches?foundItem={foundItemId}&status=CONFIRMED
GET /api/matches?foundItem={foundItemId}&lostItem={lostItemId}&status=PENDING
```

Create/update validation:

1. Matching loads the referenced found item from the Found Item Service.
2. Matching loads the referenced lost item from the Lost Item Service.
3. Both referenced resources must belong to the same `venueId`.
4. The match `venueId` is stored from the referenced resources.
5. Staff/ops can only create/update matches for JWT `venue_id`; admins can create/update across venues.

Count and histogram responses use the same shape as the Found Item Service.

Histogram buckets include only buckets that exist in the filtered data. Empty dates/weeks/months are not zero-filled.

## Pickup Service

Base path: `/api/pickups`

The pickup workflow uses two token scopes:

| Token type | Created by | Allows |
| --- | --- | --- |
| `match_view` | Staff/ops/admin via `POST /api/matches/{id}/public-link` | View, confirm, or reject a match; list pickup slots; create the first pickup |
| `pickup_manage` | `POST /api/pickups/public/{token}` | Reschedule or cancel an existing pickup |

Tokens are opaque, HMAC-signed, include the relevant `matchId`, `venueId`, optional `pickupId`, recipient email, token type, and expiry timestamp, and expire after seven days.

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `GET` | `/api/pickups/public/{token}` | - | Public magic link; returns available slots for the token venue |
| `POST` | `/api/pickups/public/{token}` | - | Public `match_view` token; books a pickup for the token match and recipient email |
| `PUT` | `/api/pickups/public/{token}` | - | Public `pickup_manage` token; reschedules an existing pickup |
| `DELETE` | `/api/pickups/public/{token}` | - | Public `pickup_manage` token; cancels an existing pickup |
| `GET` | `/api/pickups` | optional `venueId` | `ADMIN` sees all or requested venue; staff/ops own venue only |
| `PUT` | `/api/pickups/matches/{matchId}` | - | Upserts the pickup for a match; venue rules apply |
| `PUT` | `/api/pickups/{pickupId}` | - | Updates one pickup; venue rules apply |
| `DELETE` | `/api/pickups/matches/{matchId}` | - | Deletes the pickup for a match; venue rules apply |
| `DELETE` | `/api/pickups/{pickupId}` | - | Deletes one pickup; venue rules apply |
| `GET` | `/api/pickups/schedule` | optional `venueId` | Lists pickup schedules; venue rules apply |
| `POST` | `/api/pickups/schedule` | - | Creates a pickup schedule; venue rules apply |
| `PUT` | `/api/pickups/schedule/{scheduleId}` | - | Updates a pickup schedule; venue rules apply |
| `DELETE` | `/api/pickups/schedule/{scheduleId}` | - | Deletes a pickup schedule; venue rules apply |
| `GET` | `/api/pickups/email-log` | optional `recipient` | Staff/ops see own venue only; admins see all; local/test outbox for pickup-management emails |

`PickupSchedule` defines slot windows by `date`, `startTime`, `endTime`, `slotLengthInMinutes`, and `venueId`. Public slot responses are derived from these schedules and existing booked pickups.

## Notification Service

Base path: `/api/notifications`

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/notifications` | - | `ADMIN`, `STAFF`, `OPS_MANAGER`; venue rules apply |
| `GET` | `/api/notifications` | optional `email` | `ADMIN` sees all; staff/ops own venue only |
| `GET` | `/api/notifications/{id}` | - | Resource venue check |
| `PUT` | `/api/notifications/{id}` | - | Resource venue check |
| `GET` | `/api/notifications/bluePrints` | - | Currently dummy endpoint; `ADMIN`, `STAFF`, `OPS_MANAGER` |
| `GET` | `/api/notifications/bluePrints/{id}` | - | Currently dummy endpoint; `ADMIN`, `STAFF`, `OPS_MANAGER` |
| `POST` | `/api/notifications/bluePrints` | - | Currently dummy endpoint; `ADMIN`, `OPS_MANAGER` only |
| `PUT` | `/api/notifications/bluePrints/{id}` | - | Currently dummy endpoint; `ADMIN`, `OPS_MANAGER` only |

For notification writes, `STAFF` and `OPS_MANAGER` always receive `venueId` from the JWT. `ADMIN` may provide `venueId`, but can also create venue-independent notifications with `venueId: null`.

Blueprint note: once blueprints are persisted, read/write operations should also verify the blueprint `venueId`. The current dummy implementation only enforces role-based access.

## Operations Service

Base path: `/api/venues`

| Method | Endpoint | Query params | Access |
| --- | --- | --- | --- |
| `POST` | `/api/venues` | - | `ADMIN` only |
| `GET` | `/api/venues` | - | `ADMIN` sees all; staff/ops own venue only |
| `GET` | `/api/venues/{id}` | - | `ADMIN` or own venue |
| `PUT` | `/api/venues/{id}` | - | `ADMIN` or own venue |
| `DELETE` | `/api/venues/{id}` | - | `ADMIN` only |
| `GET` | `/api/venues/kpis` | - | `ADMIN` receives global KPIs; staff/ops receive own-venue KPIs |
| `GET` | `/api/venues/kpis/{id}` | - | `ADMIN` can request any venue; staff/ops only own venue |

KPI response:

```json
{
  "venueId": "00000000-0000-0000-0000-000000000000",
  "totalFoundItems": 23,
  "totalLostItems": 12,
  "totalMatches": 8,
  "pendingMatches": 3
}
```

The Operations Service aggregates these KPIs through the Found Item, Lost Item and Matching service count endpoints and forwards the caller's bearer token to preserve downstream authorization.

## Gateway Notes

The API Gateway routes external `/api/**` requests to the corresponding services. Security is still enforced in each downstream service, so bypassing the gateway does not bypass resource-server validation.

For end-to-end testing, send requests through the Gateway with:

```http
Authorization: Bearer <access-token>
```

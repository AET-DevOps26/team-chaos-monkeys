# Messaging and Domain Events

RabbitMQ carries asynchronous workflow events between services. PostgreSQL
remains the source of truth; events announce that a state change was already
persisted by the owning service.

## Broker

The current implemented broker is the domain-event RabbitMQ exchange:

| Setting | Value |
| --- | --- |
| Exchange | `foundflow.domain-events` |
| Exchange type | Topic |
| Payload format | JSON |
| Contracts | `shared/domain-events` |
| Schema versioning | Routing-key suffix, for example `.v1` |

## Routing

| Routing key | Publisher | Current consumer queue |
| --- | --- | --- |
| `lost-report.created.v1` | `lost-item-service` | `matching.lost-report-created.v1` |
| `lost-report.updated.v1` | `lost-item-service` | `matching.lost-report-updated.v1` |
| `found-item.created.v1` | `found-item-service` | `matching.found-item-created.v1` |
| `found-item.updated.v1` | `found-item-service` | `matching.found-item-updated.v1` |
| `match-candidate.created.v1` | `matching-service` | No queue bound in the current codebase |

## Planned Notification Messaging

Notifications are intended to move through RabbitMQ as a separate asynchronous
notification channel. This is not wired in the current codebase yet; today the
`notification-service` exposes REST endpoints and `match-candidate.created.v1`
has no bound notification queue.

Planned direction:

- Use RabbitMQ for all outbound notification requests, instead of coupling
  producers directly to notification delivery.
- Keep notification messages separate from domain events, for example through a
  dedicated exchange such as `foundflow.notifications`.
- Let services publish notification requests when user-facing communication is
  needed, e.g. match candidate emails, match confirmation updates, pickup
  management emails, and operational messages.
- Let `notification-service` consume those requests, render/deliver messages,
  persist delivery state in its own database, and optionally publish delivery
  outcome events.

The ownership rule stays the same: producer services own their domain state;
`notification-service` owns delivery records and delivery status.

## Payloads

### `LostReportCreatedEvent` and `LostReportUpdatedEvent`

Fields:

- `eventId`
- `occurredAt`
- `lostReportId`
- `venueId`
- `photoKey`
- `description`
- `lostAt`
- `location`
- `status`
- `attributes`

The payload intentionally excludes guest contact data. Consumers that need
private details must call the owning service through an authorized API.

### `FoundItemCreatedEvent` and `FoundItemUpdatedEvent`

Fields:

- `eventId`
- `occurredAt`
- `foundItemId`
- `venueId`
- `photoKey`
- `intakeText`
- `foundAt`
- `location`
- `status`
- `reporterId`
- `attributes`

### `MatchCandidateCreatedEvent`

Fields:

- `eventId`
- `occurredAt`
- `matchId`
- `lostReportId`
- `foundItemId`
- `venueId`
- `attributeScore`
- `semanticScore`
- `combinedScore`

The matching service emits this when the matching pipeline persists or updates
a pending candidate above the configured threshold. Consumers should be
idempotent by `matchId`.

## Ownership Rules

- Events do not transfer data ownership.
- Consumers store only the local projection/index data they own.
- Private data is not broadcast through RabbitMQ.
- Event payload classes and routing constants are versioned in
  `shared/domain-events`.

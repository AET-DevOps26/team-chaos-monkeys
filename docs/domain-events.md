# FoundFlow Domain Events

RabbitMQ carries asynchronous workflow events between services. PostgreSQL remains the source of truth; events are notifications that something already persisted.

## Broker

- Exchange: `foundflow.domain-events`
- Exchange type: topic
- Payload format: JSON
- Event contracts live in `shared/domain-events`
- Time fields are serialized as UTC instants. Routing-key suffixes such as `.v1` are the event schema version.

## Published Events

### `lost-report.created.v1`

Publisher: `lost-item-service`

First consumer: `matching-service`, queue `matching.lost-report-created.v1`

Payload type: `LostReportCreatedEvent`

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

The payload intentionally does not include guest contact data. Consumers that need private contact details must ask the owning service through an authorized API.

### `lost-report.updated.v1`

Publisher: `lost-item-service`

First consumer: `matching-service`, queue `matching.lost-report-updated.v1`

Payload type: `LostReportUpdatedEvent`

Fields match `lost-report.created.v1`. The event is emitted after a normal lost-report update so candidate matching can be recalculated from the current persisted state.

### `found-item.logged.v1`

Publisher: `found-item-service`

First consumer: `matching-service`, queue `matching.found-item-logged.v1`

Payload type: `FoundItemLoggedEvent`

Fields:

- `eventId`
- `occurredAt`
- `foundItemId`
- `venueId`
- `photoKey`
- `description`
- `foundAt`
- `locationHint`
- `status`
- `reporterId`
- `attributes`

### `found-item.updated.v1`

Publisher: `found-item-service`

First consumer: `matching-service`, queue `matching.found-item-updated.v1`

Payload type: `FoundItemUpdatedEvent`

Fields match `found-item.logged.v1`. The event is emitted after a normal found-item update so candidate matching can be recalculated from the current persisted state.

### `match-candidate.created.v1`

Publisher: `matching-service`

First consumer: none yet (intended for `notification-service`).

Payload type: `MatchCandidateCreatedEvent`

Fields:

- `eventId`
- `version`
- `occurredAt`
- `matchId`
- `lostReportId`
- `foundItemId`
- `venueId`
- `attributeScore`
- `semanticScore`
- `combinedScore`

Emitted whenever the matching pipeline persists a `Match` row whose `combinedScore >= foundflow.matching.threshold` (default `0.55`). The event is re-emitted on score updates for existing PENDING matches; consumers must be idempotent on `matchId`. Matches in `CONFIRMED` or `REJECTED` status are never re-emitted.

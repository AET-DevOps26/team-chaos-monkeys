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
- `contactEmail`
- `attributes`

The payload includes the guest contact email because matching owns the asynchronous match-invite path and cannot rely on a user JWT during RabbitMQ consumption. Consumers must treat the field as PII, avoid logging it, and only copy it into storage that is needed for the invite workflow.

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

First consumer: `matching-service`, queue `matching.match-candidate-created.v1`.

Payload type: `MatchCandidateCreatedEvent`

Fields:

- `eventId`
- `occurredAt`
- `matchId`
- `lostReportId`
- `foundItemId`
- `venueId`
- `recipientEmail`
- `attributeScore`
- `semanticScore`
- `combinedScore`

Emitted whenever the matching pipeline persists a new `Match` row whose `combinedScore >= foundflow.matching.threshold` (default `0.55`). Existing PENDING matches are re-emitted only when at least one score changes by `foundflow.matching.republish-score-delta` (default `0.01`). Consumers must be idempotent on `matchId`; the auto-invite consumer checks the local match email log and will not send a second invite for a match that already has one. Matches in `CONFIRMED` or `REJECTED` status are never re-emitted.

Auto-invite uses `foundflow.matching.auto-invite-threshold` (default `0.85`) so it is stricter than candidate creation. It does not gate on the asynchronous verify-match verdict today because verification completes after candidate creation; staff review remains the path for candidates below the auto-invite threshold.

Matching also validates incoming embedding length before inserting into `item_embeddings`. The expected length is `foundflow.matching.embedding-dim`, sourced from `EMBEDDING_DIMENSIONS` by default and aligned with the `vector(768)` migration default.

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
| `match-candidate.created.v1` | `matching-service` | `matching.match-candidate-created.v1` |
| `match-invite.requested.v1` | `matching-service` | `notification.match-invite-requested.v1` |
| `pickup-confirmation.requested.v1` | `pickup-service` | `notification.pickup-confirmation-requested.v1` |
| `password-reset.requested.v1` | `auth-service` | `notification.password-reset-requested.v1` |
| `venue.deleted.v1` | `operations-service` | `auth.venue-deleted.v1` |

Consumer queues are declared durable by the consumers. Matching queues use the
dead-letter exchange `foundflow.dlx.v1`; failed messages route to
`<queue>.dlq` with routing key `<queue>.dlq`. When renaming a queue on a
persistent broker, drain or delete the old durable queue after the rollout so
messages are not stranded under the previous name. Disposable local brokers can
be recreated with `docker compose down -v` before bringing the stack back up.

## Notification Messaging

User-facing outbound communication moves through RabbitMQ request events on the
domain-event exchange. Producers publish after their transaction commits, so
notification delivery is not triggered for rolled-back state changes.

- `matching-service` publishes `match-invite.requested.v1` when a public match
  link is created manually or by the high-confidence auto-invite listener.
- `pickup-service` publishes `pickup-confirmation.requested.v1` for pickup
  confirmation messages.
- `auth-service` publishes `password-reset.requested.v1` for password-reset
  emails.
- `notification-service` consumes those requests, renders/delivers messages,
  and persists delivery records in its own database.

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
- `contactEmail`
- `attributes`

The contact email is included so matching can persist the recipient on
candidate matches and auto-invite high-confidence matches without an extra
synchronous lookup.

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
- `recipientEmail`
- `attributeScore`
- `semanticScore`
- `combinedScore`

The matching service emits this when the matching pipeline persists or updates
a pending candidate above the configured threshold. Consumers should be
idempotent by `matchId`.

## Ownership Rules

- Events do not transfer data ownership.
- Consumers store only the local projection/index data they own.
- Private data is broadcast only when it is required by an asynchronous
  workflow, such as match invite delivery.
- Event payload classes and routing constants are versioned in
  `shared/domain-events`.

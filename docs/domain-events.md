# FoundFlow Domain Events

RabbitMQ carries asynchronous workflow events between services. PostgreSQL remains the source of truth; events are notifications that something already persisted.

## Broker

- Exchange: `foundflow.domain-events`
- Exchange type: topic
- Payload format: JSON
- Event contracts live in `shared/domain-events`

## Published Events

### `lost-report.created.v1`

Publisher: `lost-item-service`

First consumer: `matching-service`, queue `matching.lost-report-created.v1`

Payload type: `LostReportCreatedEvent`

Fields:

- `eventId`
- `version`
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

### `found-item.logged.v1`

Publisher: `found-item-service`

First consumer: `matching-service`, queue `matching.found-item-logged.v1`

Payload type: `FoundItemLoggedEvent`

Fields:

- `eventId`
- `version`
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

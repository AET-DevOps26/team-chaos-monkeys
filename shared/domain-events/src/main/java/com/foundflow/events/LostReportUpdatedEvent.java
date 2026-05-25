package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record LostReportUpdatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID lostReportId,
        UUID venueId,
        String photoKey,
        String description,
        Instant lostAt,
        String location,
        String status,
        ItemAttributesPayload attributes
) {
}

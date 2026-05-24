package com.foundflow.events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record LostReportCreatedEvent(
        UUID eventId,
        int version,
        Instant occurredAt,
        UUID lostReportId,
        UUID venueId,
        String photoKey,
        String description,
        LocalDateTime lostAt,
        String location,
        String status,
        ItemAttributesPayload attributes
) {
}

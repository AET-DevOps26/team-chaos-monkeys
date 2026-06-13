package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record FoundItemCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID foundItemId,
        UUID venueId,
        String photoKey,
        String intakeText,
        Instant foundAt,
        String location,
        String status,
        UUID reporterId,
        ItemAttributesPayload attributes
) {
}

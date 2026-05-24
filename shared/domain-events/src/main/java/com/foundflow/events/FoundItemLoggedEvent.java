package com.foundflow.events;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

public record FoundItemLoggedEvent(
        UUID eventId,
        int version,
        Instant occurredAt,
        UUID foundItemId,
        UUID venueId,
        String photoKey,
        String description,
        LocalDateTime foundAt,
        String locationHint,
        String status,
        UUID reporterId,
        ItemAttributesPayload attributes
) {
}

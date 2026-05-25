package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record FoundItemLoggedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID foundItemId,
        UUID venueId,
        String photoKey,
        String description,
        Instant foundAt,
        String locationHint,
        String status,
        UUID reporterId,
        ItemAttributesPayload attributes
) {
}

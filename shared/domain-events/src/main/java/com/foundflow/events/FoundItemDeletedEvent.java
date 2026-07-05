package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record FoundItemDeletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID foundItemId,
        UUID venueId
) {
}

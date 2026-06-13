package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record VenueDeletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID venueId
) {
}

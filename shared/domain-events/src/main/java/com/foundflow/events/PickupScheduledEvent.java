package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record PickupScheduledEvent(
        UUID eventId,
        Instant occurredAt,
        UUID pickupId,
        UUID matchId,
        UUID venueId
) {
}

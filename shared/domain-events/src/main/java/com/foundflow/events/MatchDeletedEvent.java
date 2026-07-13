package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record MatchDeletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID matchId,
        UUID venueId
) {
}

package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record MatchInviteRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID matchId,
        String recipient,
        UUID venueId,
        String matchUrl
) {
}

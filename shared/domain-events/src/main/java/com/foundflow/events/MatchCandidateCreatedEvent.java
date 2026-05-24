package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record MatchCandidateCreatedEvent(
        UUID eventId,
        int version,
        Instant occurredAt,
        UUID matchId,
        UUID lostReportId,
        UUID foundItemId,
        UUID venueId,
        float attributeScore,
        float semanticScore,
        float combinedScore
) {
}

package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record MatchCandidateCreatedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID matchId,
        UUID lostReportId,
        UUID foundItemId,
        UUID venueId,
        String recipientEmail,
        float attributeScore,
        float semanticScore,
        float combinedScore
) {
}

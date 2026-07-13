package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record LostReportDeletedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID lostReportId,
        UUID venueId
) {
}

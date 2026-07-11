package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record FoundItemReservationRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID foundItemId,
        UUID venueId
) {
}

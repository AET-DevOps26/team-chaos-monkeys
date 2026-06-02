package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record PickupConfirmationRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID pickupId,
        UUID matchId,
        String recipient,
        UUID venueId
) {
}

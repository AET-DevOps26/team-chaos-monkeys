package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

public record PasswordResetRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID userId,
        UUID venueId,
        String recipient,
        String resetUrl
) {
}

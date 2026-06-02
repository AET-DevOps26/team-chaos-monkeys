package com.foundflow.pickup.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PickupEmailLogResponse(
        UUID id,
        String recipient,
        UUID venueId,
        String subject,
        String body,
        String magicLink,
        LocalDateTime sentAt
) {
}

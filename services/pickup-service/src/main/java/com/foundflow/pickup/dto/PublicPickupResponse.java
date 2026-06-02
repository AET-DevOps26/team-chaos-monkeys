package com.foundflow.pickup.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record PublicPickupResponse(
        UUID id,
        LocalDateTime pickupAt,
        UUID venueId,
        UUID matchId,
        String email,
        String manageUrl
) {
}

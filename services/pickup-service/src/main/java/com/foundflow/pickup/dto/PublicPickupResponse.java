package com.foundflow.pickup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.UUID;

public record PublicPickupResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime pickupAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID venueId,
        UUID matchId,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String manageUrl
) {
}

package com.foundflow.pickup.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

public record PickupSlotResponse(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime startsAt,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LocalDateTime endsAt,
        boolean available
) {
}

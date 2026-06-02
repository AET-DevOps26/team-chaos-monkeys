package com.foundflow.pickup.dto;

import java.time.LocalDateTime;

public record PickupSlotResponse(
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        boolean available
) {
}

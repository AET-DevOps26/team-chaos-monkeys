package com.foundflow.operations.dto;

import java.util.UUID;

public record VenueKpiResponse(
        UUID venueId,
        long totalFoundItems,
        long totalLostItems,
        long totalMatches,
        long pendingMatches
) {
}

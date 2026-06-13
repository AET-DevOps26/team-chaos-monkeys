package com.foundflow.operations.dto;

import java.util.UUID;

public record PublicVenueResponse(
        UUID venueId,
        String name
) {
}

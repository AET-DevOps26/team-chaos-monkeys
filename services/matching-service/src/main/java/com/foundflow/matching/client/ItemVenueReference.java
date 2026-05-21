package com.foundflow.matching.client;

import java.util.UUID;

public record ItemVenueReference(
        UUID id,
        UUID venueId
) {
}

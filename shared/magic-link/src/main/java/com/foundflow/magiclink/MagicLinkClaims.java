package com.foundflow.magiclink;

import java.util.UUID;

public record MagicLinkClaims(
        String type,
        UUID matchId,
        UUID pickupId,
        UUID venueId,
        String email,
        long expiresAt
) {
}

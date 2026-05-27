package com.foundflow.matching.service;

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

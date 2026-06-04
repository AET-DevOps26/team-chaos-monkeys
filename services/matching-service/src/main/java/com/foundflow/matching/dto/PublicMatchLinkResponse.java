package com.foundflow.matching.dto;

public record PublicMatchLinkResponse(
        String token,
        String matchUrl,
        String pickupUrl
) {
}

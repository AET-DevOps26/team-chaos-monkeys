package com.foundflow.matching.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchEmailLogResponse(
        UUID id,
        String recipient,
        UUID venueId,
        UUID matchId,
        String subject,
        String body,
        String magicLink,
        LocalDateTime sentAt
) {
}

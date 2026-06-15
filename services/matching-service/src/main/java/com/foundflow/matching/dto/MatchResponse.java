package com.foundflow.matching.dto;

import com.foundflow.matching.domain.MatchStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID foundItemId,
        UUID lostReportId,
        UUID venueId,
        String recipientEmail,
        MatchStatus status,
        float attributeScore,
        float semanticScore,
        float combinedScore,
        LocalDateTime createdAt
) {
}

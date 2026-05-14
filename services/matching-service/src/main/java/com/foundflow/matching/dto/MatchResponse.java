package com.foundflow.matching.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record MatchResponse(
        UUID id,
        UUID foundItemId,
        UUID lostReportId,
        float attributeScore,
        float semanticScore,
        float combinedScore,
        LocalDateTime createdAt
) {
}
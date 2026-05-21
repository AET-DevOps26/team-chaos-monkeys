package com.foundflow.matching.dto;

import com.foundflow.matching.domain.MatchStatus;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateMatchRequest(
        @NotNull
        UUID foundItemId,

        @NotNull
        UUID lostReportId,

        UUID venueId,

        @NotNull
        MatchStatus status,

        @NotNull
        Float attributeScore,
        @NotNull
        Float semanticScore,
        @NotNull
        Float combinedScore
) {
}

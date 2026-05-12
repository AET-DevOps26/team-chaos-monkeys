package com.foundflow.matching.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateMatchRequest(
        @NotNull
        UUID foundItemId,

        @NotNull
        UUID lostReportId,

        float attributeScore,
        float semanticScore,
        float combinedScore
) {
}
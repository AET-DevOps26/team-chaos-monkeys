package com.foundflow.matching.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record UpdateMatchRequest(
        @NotNull
        UUID foundItemId,

        @NotNull
        UUID lostReportId,

        @NotNull
        Float attributeScore,
        @NotNull
        Float semanticScore,
        @NotNull
        Float combinedScore
) {
}
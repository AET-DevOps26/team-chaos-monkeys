package com.foundflow.matching.domain;

import java.time.OffsetDateTime;

public record MatchVerification(
        String verdict,
        Float confidence,
        String rationale,
        String modelProvider,
        String modelName,
        OffsetDateTime completedAt
) {
}

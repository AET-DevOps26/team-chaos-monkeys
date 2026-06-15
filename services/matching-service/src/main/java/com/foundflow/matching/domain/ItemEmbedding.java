package com.foundflow.matching.domain;

import java.util.UUID;

public record ItemEmbedding(
        UUID id,
        ItemType itemType,
        UUID itemId,
        UUID venueId,
        String category,
        String contactEmail,
        float[] embedding,
        String textSource
) {
}

package com.foundflow.matching.repository;

import com.foundflow.matching.domain.ItemType;

import java.util.UUID;

/**
 * A pgvector KNN hit for free-text item search. Unlike {@link SimilarItemEmbedding} (candidate
 * matching, single opposite type) this carries the {@code itemType} because a staff search can
 * span both lost reports and found items.
 */
public record ScopedSimilarItem(
        UUID itemId,
        ItemType itemType,
        String category,
        String textSource,
        float cosineDistance
) {
}

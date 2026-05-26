package com.foundflow.matching.repository;

import java.util.UUID;

public record SimilarItemEmbedding(
        UUID itemId,
        String category,
        float cosineDistance
) {
}

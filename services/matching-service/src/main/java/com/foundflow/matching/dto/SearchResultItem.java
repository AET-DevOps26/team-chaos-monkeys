package com.foundflow.matching.dto;

import com.foundflow.matching.domain.ItemType;

/**
 * A retrieved item card for the staff search UI. {@code id} matches the entries in
 * {@link ItemSearchResponse#citations()} so the frontend can render which items were cited.
 */
public record SearchResultItem(
        String id,
        ItemType itemType,
        String category,
        String text,
        float distance
) {
}

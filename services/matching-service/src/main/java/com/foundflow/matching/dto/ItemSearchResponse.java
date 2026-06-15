package com.foundflow.matching.dto;

import java.util.List;

/**
 * Result of a staff free-text item search.
 *
 * @param answer    grounded natural-language answer, or {@code null} when degraded (genai
 *                  unavailable / disabled) or when nothing was retrieved.
 * @param grounded  whether the answer is grounded in the retrieved items; {@code false} signals
 *                  low confidence / "no matching items".
 * @param citations the retrieved item ids the answer cited (subset of {@link #results()} ids).
 * @param results   the retrieved item cards, ranked by similarity to the query.
 */
public record ItemSearchResponse(
        String answer,
        boolean grounded,
        List<String> citations,
        List<SearchResultItem> results
) {
}

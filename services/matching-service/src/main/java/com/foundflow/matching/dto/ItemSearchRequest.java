package com.foundflow.matching.dto;

import com.foundflow.matching.domain.SearchScope;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Staff free-text item search request.
 *
 * @param query the natural-language query (drives embedding + answer); required.
 * @param scope optional explicit item-type filter; defaults to {@link SearchScope#BOTH}.
 * @param k     optional number of items to retrieve; defaults to the configured value.
 */
public record ItemSearchRequest(
        @NotBlank @Size(max = 4000) String query,
        SearchScope scope,
        @Min(1) @Max(32) Integer k
) {
}

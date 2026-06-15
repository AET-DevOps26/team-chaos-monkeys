package com.foundflow.matching.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

import java.util.Locale;

/**
 * Which slice of the item corpus a staff free-text search should retrieve from.
 * The natural-language query always drives ranking; this is an explicit hard filter on top.
 */
public enum SearchScope {

    LOST,
    FOUND,
    BOTH;

    @JsonCreator
    public static SearchScope fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BOTH;
        }
        return SearchScope.valueOf(value.strip().toUpperCase(Locale.ROOT));
    }

    /** The retrieval item-type filter, or {@code null} when both item types should be searched. */
    public ItemType itemTypeOrNull() {
        return switch (this) {
            case LOST -> ItemType.LOST;
            case FOUND -> ItemType.FOUND;
            case BOTH -> null;
        };
    }
}

package com.foundflow.lostitem.dto;

import java.util.List;

public record ItemAttributesDto(
        String category,
        String description,
        String brand,
        String color,
        List<String> marks
) {
    /**
     * Backward-compatible constructor for call sites that predate the
     * generated {@code description} field; defaults it to {@code null}.
     */
    public ItemAttributesDto(String category, String brand, String color, List<String> marks) {
        this(category, null, brand, color, marks);
    }
}
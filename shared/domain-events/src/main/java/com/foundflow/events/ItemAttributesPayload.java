package com.foundflow.events;

import java.util.List;

public record ItemAttributesPayload(
        String category,
        String description,
        String brand,
        String color,
        List<String> marks
) {
    /**
     * Backward-compatible constructor for call sites that predate the
     * generated {@code description} field. Defaults description to {@code null}
     * so existing publishers and tests stay valid; messages serialized without
     * the field also deserialize through the canonical constructor.
     */
    public ItemAttributesPayload(String category, String brand, String color, List<String> marks) {
        this(category, null, brand, color, marks);
    }
}

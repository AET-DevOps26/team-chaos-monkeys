package com.foundflow.events;

import java.util.List;

public record ItemAttributesPayload(
        String category,
        String brand,
        String color,
        List<String> marks
) {
}

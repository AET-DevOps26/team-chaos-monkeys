package com.foundflow.founditem.dto;

import java.util.List;

public record ItemAttributesDto(
        String category,
        String brand,
        String color,
        List<String> marks
) {
}
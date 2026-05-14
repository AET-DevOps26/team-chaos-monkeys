package com.foundflow.operations.dto;

import java.util.UUID;

public record VenueResponse(
        UUID id,
        String name,
        String tone,
        String defaultLanguage
) {
}
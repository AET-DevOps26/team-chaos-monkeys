package com.foundflow.matching.dto;

import com.foundflow.events.ItemAttributesPayload;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

public record PublicFoundItemResponse(
        UUID id,
        String description,
        LocalDateTime foundAt,
        String locationHint,
        String status,
        ItemAttributesPayload attributes,
        URI photoUrl
) {
}

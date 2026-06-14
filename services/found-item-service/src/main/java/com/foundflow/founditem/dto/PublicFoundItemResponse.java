package com.foundflow.founditem.dto;

import com.foundflow.founditem.domain.ItemStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

public record PublicFoundItemResponse(
        UUID id,
        String description,
        LocalDateTime foundAt,
        String locationHint,
        ItemStatus status,
        ItemAttributesDto attributes,
        URI photoUrl
) {
}

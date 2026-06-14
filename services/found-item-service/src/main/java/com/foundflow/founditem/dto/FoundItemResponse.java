package com.foundflow.founditem.dto;

import com.foundflow.founditem.domain.ItemStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record FoundItemResponse(
        UUID id,
        String photoKey,
        String intakeText,
        LocalDateTime foundAt,
        String location,
        ItemStatus status,
        UUID venueId,
        UUID reporterId,
        ItemAttributesDto attributes
) {
}
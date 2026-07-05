package com.foundflow.founditem.dto;

import com.foundflow.founditem.domain.ItemStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

public record FoundItemResponse(
        UUID id,
        String photoKey,
        URI photoUrl,
        String intakeText,
        LocalDateTime foundAt,
        String location,
        ItemStatus status,
        UUID venueId,
        UUID reporterId,
        ItemAttributesDto attributes
) {
    public FoundItemResponse(
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
        this(
                id,
                photoKey,
                null,
                intakeText,
                foundAt,
                location,
                status,
                venueId,
                reporterId,
                attributes
        );
    }
}

package com.foundflow.founditem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateFoundItemRequest(
        String photoKey,
        String description,

        @NotNull
        LocalDateTime foundAt,

        String locationHint,

        @NotNull
        UUID venueId,

        @NotNull
        UUID reporterId,

        @Valid
        ItemAttributesDto attributes
) {
}
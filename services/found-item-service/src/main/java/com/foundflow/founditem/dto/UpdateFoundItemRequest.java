package com.foundflow.founditem.dto;

import com.foundflow.founditem.domain.ItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateFoundItemRequest(
        String description,

        @NotNull
        LocalDateTime foundAt,

        String locationHint,

        @NotNull
        ItemStatus status,

        @NotNull
        UUID venueId,

        @NotNull
        UUID reporterId,

        @Valid
        ItemAttributesDto attributes
) {
}

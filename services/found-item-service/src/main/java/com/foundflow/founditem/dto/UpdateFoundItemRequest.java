package com.foundflow.founditem.dto;

import com.foundflow.founditem.domain.ItemStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateFoundItemRequest(
        String intakeText,

        @NotNull
        LocalDateTime foundAt,

        String location,

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

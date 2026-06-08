package com.foundflow.founditem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateFoundItemRequest(
        @Size(max = 2000)
        String intakeText,

        @NotNull
        LocalDateTime foundAt,

        @NotNull
        UUID venueId,

        UUID reporterId,

        @Valid
        ItemAttributesDto attributes
) {
}

package com.foundflow.founditem.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateFoundItemRequest(
        String intakeText,

        @NotNull
        LocalDateTime foundAt,

        @NotNull
        UUID venueId,

        @NotNull
        UUID reporterId
) {
}

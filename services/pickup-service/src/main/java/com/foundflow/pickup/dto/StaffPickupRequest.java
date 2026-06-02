package com.foundflow.pickup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record StaffPickupRequest(
        @NotNull
        LocalDateTime pickupAt,

        UUID venueId,

        UUID matchId,

        @NotBlank
        @Email
        String email
) {
}

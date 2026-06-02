package com.foundflow.pickup.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreatePickupRequest(
        @NotNull
        LocalDateTime pickupAt,

        @NotBlank
        @Email
        String email
) {
}

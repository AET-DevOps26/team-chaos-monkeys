package com.foundflow.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.UUID;

public record UpdateNotificationRequest(
        @NotNull
        UUID matchId,

        UUID venueId,

        @NotBlank
        @Email
        String recipientAddress,

        @NotBlank
        String language,

        @NotBlank
        String subject,

        @NotBlank
        String header,

        @NotBlank
        String body,

        LocalDateTime sentAt
) {
}

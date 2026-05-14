package com.foundflow.notification.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateNotificationRequest(
        @NotNull
        UUID matchId,

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
        String body
) {
}
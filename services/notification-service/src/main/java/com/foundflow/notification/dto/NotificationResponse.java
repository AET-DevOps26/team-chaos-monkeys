package com.foundflow.notification.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID matchId,
        UUID venueId,
        String recipientAddress,
        String language,
        String subject,
        String header,
        String body,
        LocalDateTime sentAt
) {
}

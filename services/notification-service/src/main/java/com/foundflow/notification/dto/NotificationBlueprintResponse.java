package com.foundflow.notification.dto;

import java.util.UUID;

public record NotificationBlueprintResponse(
        UUID id,
        String name,
        String language,
        String subject,
        String header,
        String body
) {
}

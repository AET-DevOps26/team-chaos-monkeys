package com.foundflow.photo.storage;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

final class PhotoKeyFactory {

    private final String domain;
    private final Clock clock;

    PhotoKeyFactory(String domain, Clock clock) {
        this.domain = domain;
        this.clock = clock;
    }

    String createKey(String contentType) {
        LocalDate today = LocalDate.now(clock);
        return "%s/%04d/%02d/%s.%s".formatted(
                domain,
                today.getYear(),
                today.getMonthValue(),
                UUID.randomUUID(),
                extensionFor(contentType)
        );
    }

    String contentTypeFor(String photoKey) {
        String lowerCaseKey = photoKey.toLowerCase(Locale.ROOT);
        if (lowerCaseKey.endsWith(".jpg") || lowerCaseKey.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lowerCaseKey.endsWith(".png")) {
            return "image/png";
        }
        if (lowerCaseKey.endsWith(".webp")) {
            return "image/webp";
        }
        return "application/octet-stream";
    }

    private String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new PhotoStorageException("Unsupported photo content type: " + contentType);
        };
    }
}

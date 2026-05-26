package com.foundflow.photo.storage;

import java.util.List;
import java.util.Set;

public final class PhotoConstraints {

    public static final long MAX_PHOTO_SIZE_BYTES = 10L * 1024L * 1024L;

    public static final List<String> ALLOWED_CONTENT_TYPES = List.of(
            "image/jpeg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_SET = Set.copyOf(ALLOWED_CONTENT_TYPES);

    private PhotoConstraints() {
    }

    public static Violation check(String contentType, long sizeBytes) {
        if (sizeBytes <= 0L) {
            return Violation.EMPTY;
        }
        if (sizeBytes > MAX_PHOTO_SIZE_BYTES) {
            return Violation.TOO_LARGE;
        }
        if (contentType == null || !ALLOWED_SET.contains(contentType)) {
            return Violation.UNSUPPORTED_TYPE;
        }
        return null;
    }

    public enum Violation {
        EMPTY("Photo must not be empty."),
        TOO_LARGE("Photo must be at most 10 MB."),
        UNSUPPORTED_TYPE("Unsupported photo content type.");

        private final String message;

        Violation(String message) {
            this.message = message;
        }

        public String message() {
            return message;
        }
    }
}

package com.foundflow.photo.storage;

import java.io.InputStream;

public record PhotoData(
        InputStream content,
        String contentType,
        long sizeBytes
) {
}

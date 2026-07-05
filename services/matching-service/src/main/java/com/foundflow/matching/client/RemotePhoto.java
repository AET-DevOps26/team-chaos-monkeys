package com.foundflow.matching.client;

import org.springframework.http.MediaType;

public record RemotePhoto(
        byte[] content,
        MediaType contentType,
        long sizeBytes
) {
}

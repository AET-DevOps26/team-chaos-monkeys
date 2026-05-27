package com.foundflow.genai.client;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "genai")
public record GenaiProperties(
        String baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        String apiKey,
        boolean enabled
) {
}

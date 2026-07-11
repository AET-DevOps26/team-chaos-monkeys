package com.foundflow.operations.client;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties(prefix = "foundflow.services.operations")
public record OperationsClientProperties(
        @DefaultValue("http://operations-service:8086") String baseUrl,
        @DefaultValue("PT2S") Duration connectTimeout,
        @DefaultValue("PT5S") Duration readTimeout
) {
}

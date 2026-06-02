package com.foundflow.magiclink;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "foundflow.magic-link")
public record MagicLinkProperties(
        String secret,
        @DefaultValue("7") long ttlDays
) {
}

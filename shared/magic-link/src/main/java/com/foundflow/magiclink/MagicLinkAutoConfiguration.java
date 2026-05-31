package com.foundflow.magiclink;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(MagicLinkProperties.class)
public class MagicLinkAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MagicLinkService magicLinkService(MagicLinkProperties properties) {
        return new MagicLinkService(properties.secret(), properties.ttlDays());
    }
}

package com.foundflow.genai.client;

import com.foundflow.photo.storage.PhotoStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = GenaiClientAutoConfiguration.class)
@AutoConfigureAfter(name = "com.foundflow.photo.storage.PhotoStorageAutoConfiguration")
@ConditionalOnClass(PhotoStorage.class)
@EnableConfigurationProperties(GenaiProperties.class)
public class AttributeExtractionAutoConfiguration {

    @Bean
    @ConditionalOnBean(PhotoStorage.class)
    @ConditionalOnMissingBean
    public AttributeExtractionService attributeExtractionService(
            GenaiClient genaiClient,
            PhotoStorage photoStorage,
            GenaiProperties properties
    ) {
        return new AttributeExtractionService(genaiClient, photoStorage, properties);
    }
}

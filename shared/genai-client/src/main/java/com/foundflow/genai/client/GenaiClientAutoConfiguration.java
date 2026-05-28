package com.foundflow.genai.client;

import com.foundflow.photo.storage.PhotoStorage;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration for the GenAI client.
 *
 * <p>Activates whenever a service depends on this module — no per-service
 * wiring required. The {@link GenaiClient} bean is created from
 * {@link GenaiProperties} (prefix {@code genai.}) and the
 * {@link AttributeExtractionService} is wired from the {@link GenaiClient}
 * plus the consumer's {@link PhotoStorage} bean (contributed by the
 * photo-storage shared module).
 */
@AutoConfiguration
@AutoConfigureAfter(name = "com.foundflow.photo.storage.PhotoStorageAutoConfiguration")
@EnableConfigurationProperties(GenaiProperties.class)
public class GenaiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GenaiClient genaiClient(GenaiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("X-Internal-Token", properties.apiKey());
        }

        return new GenaiClient(builder.build());
    }

    @Bean
    @ConditionalOnMissingBean
    public AttributeExtractionService attributeExtractionService(
            GenaiClient genaiClient,
            PhotoStorage photoStorage,
            GenaiProperties properties
    ) {
        return new AttributeExtractionService(genaiClient, photoStorage, properties);
    }
}

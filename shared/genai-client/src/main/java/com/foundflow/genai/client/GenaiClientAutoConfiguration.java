package com.foundflow.genai.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
 * <p>Always-on: registers the {@link GenaiClient} bean from
 * {@link GenaiProperties} (prefix {@code genai.}). The
 * {@link AttributeExtractionService} bean lives in
 * {@link AttributeExtractionAutoConfiguration} and only activates when the
 * photo-storage module is on the classpath, so consumers that only need
 * embeddings (e.g. matching-service) don't have to pull photo-storage.
 */
@AutoConfiguration
@EnableConfigurationProperties(GenaiProperties.class)
public class GenaiClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public GenaiClient genaiClient(
            GenaiProperties properties,
            ObjectProvider<RestClient.Builder> restClientBuilder
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        // Boot's auto-configured builder carries the ObservationRegistry, so
        // outbound calls get traceparent; fall back for non-Boot test contexts.
        RestClient.Builder builder = restClientBuilder
                .getIfAvailable(RestClient::builder)
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        if (properties.apiKey() != null && !properties.apiKey().isBlank()) {
            builder.defaultHeader("X-Internal-Token", properties.apiKey());
        }

        return new GenaiClient(builder.build());
    }
}

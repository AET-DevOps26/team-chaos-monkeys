package com.foundflow.operations.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@AutoConfiguration
@EnableConfigurationProperties(OperationsClientProperties.class)
public class OperationsClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public OperationsVenueClient operationsVenueClient(
            OperationsClientProperties properties,
            ObjectProvider<RestClient.Builder> restClientBuilder
    ) {
        // SimpleClientHttpRequestFactory keeps this on HTTP/1.1 and bounds the
        // call so a slow operations-service cannot hang intake. The Boot-provided
        // builder carries the ObservationRegistry, so the call gets traceparent.
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        RestClient restClient = restClientBuilder
                .getIfAvailable(RestClient::builder)
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();

        return new OperationsVenueClient(restClient);
    }
}

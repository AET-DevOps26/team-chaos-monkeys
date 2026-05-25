package com.foundflow.matching.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.List;

@Component
public class GenAiClient {

    private final RestClient restClient;

    public GenAiClient(
            @Value("${foundflow.services.genai.base-url:http://genai-service:8000}")
            String baseUrl,
            @Value("${foundflow.services.genai.connect-timeout-ms:2000}")
            int connectTimeoutMs,
            @Value("${foundflow.services.genai.read-timeout-ms:10000}")
            int readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    public float[] embed(String text) {
        EmbedResponse response;
        try {
            response = restClient
                    .post()
                    .uri("/embed")
                    .body(new EmbedRequest(List.of(text)))
                    .retrieve()
                    .body(EmbedResponse.class);
        } catch (HttpClientErrorException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GenAI service returned " + exception.getStatusCode() + " for /embed.",
                    exception
            );
        }

        if (response == null || response.embeddings() == null || response.embeddings().isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "GenAI service returned no embeddings."
            );
        }

        List<Float> first = response.embeddings().get(0);
        float[] vector = new float[first.size()];
        for (int i = 0; i < first.size(); i++) {
            vector[i] = first.get(i);
        }
        return vector;
    }

    record EmbedRequest(List<String> texts) {
    }

    record EmbedResponse(
            List<List<Float>> embeddings,
            @JsonProperty("dimensions") int dimensions
    ) {
    }
}

package com.foundflow.genai.client;

import com.foundflow.genai.client.model.EmbedRequest;
import com.foundflow.genai.client.model.EmbedResponse;
import com.foundflow.genai.client.model.ExtractAttributesRequest;
import com.foundflow.genai.client.model.ExtractAttributesResponse;
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class GenaiClient {

    private final RestClient restClient;

    public GenaiClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public ExtractAttributesResponse extractAttributes(ExtractAttributesRequest request) throws RestClientException {
        return restClient.post()
                .uri("/extract-attributes")
                .body(request)
                .retrieve()
                .body(ExtractAttributesResponse.class);
    }

    public EmbedResponse embed(EmbedRequest request) throws RestClientException {
        return restClient.post()
                .uri("/embed")
                .body(request)
                .retrieve()
                .body(EmbedResponse.class);
    }

    public VerifyMatchResponse verifyMatch(VerifyMatchRequest request) throws RestClientException {
        return restClient.post()
                .uri("/verify-match")
                .body(request)
                .retrieve()
                .body(VerifyMatchResponse.class);
    }
}

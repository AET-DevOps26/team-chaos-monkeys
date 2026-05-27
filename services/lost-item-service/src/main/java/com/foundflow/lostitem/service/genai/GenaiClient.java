package com.foundflow.lostitem.service.genai;

import com.foundflow.lostitem.genai.client.model.ExtractAttributesRequest;
import com.foundflow.lostitem.genai.client.model.ExtractAttributesResponse;
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
}

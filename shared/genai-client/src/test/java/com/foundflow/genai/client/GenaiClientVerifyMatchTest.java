package com.foundflow.genai.client;

import com.foundflow.genai.client.model.ItemSide;
import com.foundflow.genai.client.model.VerifyMatchRequest;
import com.foundflow.genai.client.model.VerifyMatchResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class GenaiClientVerifyMatchTest {

    WireMockServer wm;
    GenaiClient client;

    @BeforeEach
    void start() {
        wm = new WireMockServer(options().dynamicPort());
        wm.start();
        client = new GenaiClient(RestClient.builder().baseUrl(wm.baseUrl()).build());
    }

    @AfterEach
    void stop() {
        wm.stop();
    }

    @Test
    void postsToVerifyMatchAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .withRequestBody(matchingJsonPath("$.lost.description", equalTo("blue jacket lobby")))
                .withRequestBody(matchingJsonPath("$.found.description", equalTo("navy jacket reception")))
                .willReturn(okJson("""
                        {
                          "verdict": "match",
                          "confidence": 0.87,
                          "rationale": "Both describe a navy/blue jacket near reception/lobby.",
                          "modelInfo": { "provider": "openai", "model": "gpt-4o-mini" }
                        }
                        """)));

        // ItemSide is the generated schema for both "lost" and "found" sides
        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new ItemSide().description("blue jacket lobby"))
                .found(new ItemSide().description("navy jacket reception"));

        VerifyMatchResponse resp = client.verifyMatch(req);

        assertThat(resp.getVerdict().getValue()).isEqualTo("match");
        assertThat(resp.getConfidence()).isCloseTo(0.87f, within(0.001f));
        assertThat(resp.getRationale()).contains("navy/blue jacket");
        assertThat(resp.getModelInfo().getProvider().getValue()).isEqualTo("openai");
        assertThat(resp.getModelInfo().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void surfaces504AsServerErrorException() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .willReturn(aResponse().withStatus(504)));

        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new ItemSide().description("x"))
                .found(new ItemSide().description("y"));

        assertThatThrownBy(() -> client.verifyMatch(req))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    void surfaces429AsClientErrorException() {
        wm.stubFor(post(urlEqualTo("/verify-match"))
                .willReturn(aResponse().withStatus(429)));

        VerifyMatchRequest req = new VerifyMatchRequest()
                .lost(new ItemSide().description("x"))
                .found(new ItemSide().description("y"));

        assertThatThrownBy(() -> client.verifyMatch(req))
                .isInstanceOf(org.springframework.web.client.HttpClientErrorException.TooManyRequests.class);
    }
}

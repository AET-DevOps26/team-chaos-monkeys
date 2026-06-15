package com.foundflow.genai.client;

import com.foundflow.genai.client.model.AnswerRequest;
import com.foundflow.genai.client.model.AnswerResponse;
import com.foundflow.genai.client.model.SearchSnippet;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenaiClientAnswerTest {

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
    void postsToAnswerAndParsesResponse() {
        wm.stubFor(post(urlEqualTo("/answer"))
                .withRequestBody(matchingJsonPath("$.query", equalTo("black leather wallet near lobby")))
                .withRequestBody(matchingJsonPath("$.snippets[0].id", equalTo("11111111-1111-1111-1111-111111111111")))
                .withRequestBody(matchingJsonPath("$.snippets[0].itemType", equalTo("found_item")))
                .willReturn(okJson("""
                        {
                          "answer": "The closest match is a black leather wallet found near the lobby [1].",
                          "citations": ["11111111-1111-1111-1111-111111111111"],
                          "grounded": true,
                          "modelInfo": { "provider": "openai", "model": "gpt-4o-mini" }
                        }
                        """)));

        AnswerRequest req = new AnswerRequest()
                .query("black leather wallet near lobby")
                .snippets(List.of(new SearchSnippet()
                        .id("11111111-1111-1111-1111-111111111111")
                        .itemType(SearchSnippet.ItemTypeEnum.FOUND_ITEM)
                        .text("Black leather wallet found near the lobby on Tuesday.")));

        AnswerResponse resp = client.answer(req);

        assertThat(resp.getAnswer()).contains("black leather wallet");
        assertThat(resp.getCitations()).containsExactly("11111111-1111-1111-1111-111111111111");
        assertThat(resp.getGrounded()).isTrue();
        assertThat(resp.getModelInfo().getProvider().getValue()).isEqualTo("openai");
        assertThat(resp.getModelInfo().getModel()).isEqualTo("gpt-4o-mini");
    }

    @Test
    void surfaces504AsServerErrorException() {
        wm.stubFor(post(urlEqualTo("/answer"))
                .willReturn(aResponse().withStatus(504)));

        AnswerRequest req = new AnswerRequest()
                .query("anything")
                .snippets(List.of());

        assertThatThrownBy(() -> client.answer(req))
                .isInstanceOf(HttpServerErrorException.class);
    }
}

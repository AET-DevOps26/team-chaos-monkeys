package com.foundflow.genai.client;

import com.foundflow.genai.client.model.DiagnosticResponse;
import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;

class GenaiClientDiagnosticTest {

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
    void diagnostic_parsesDimensionsFields() {
        wm.stubFor(get(urlEqualTo("/_diagnostic"))
                .willReturn(okJson("""
                        {
                          "provider": "local",
                          "chat_ok": true,
                          "embed_ok": true,
                          "chat_latency_ms": 100,
                          "embed_latency_ms": 50,
                          "chat_model": "llama3.2:3b",
                          "embed_model": "nomic-embed-text",
                          "embed_dimensions_configured": 768,
                          "embed_dimensions_actual": 768
                        }
                        """)));

        DiagnosticResponse r = client.diagnostic();

        assertThat(r.getEmbedDimensionsConfigured()).isEqualTo(768);
        assertThat(r.getEmbedDimensionsActual()).isEqualTo(768);
        assertThat(r.getEmbedModel()).isEqualTo("nomic-embed-text");
    }
}

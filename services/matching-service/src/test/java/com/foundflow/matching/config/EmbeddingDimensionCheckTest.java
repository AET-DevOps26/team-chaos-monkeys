package com.foundflow.matching.config;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.DiagnosticResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddingDimensionCheckTest {

    GenaiClient client;

    @BeforeEach
    void setUp() {
        client = mock(GenaiClient.class);
    }

    private DiagnosticResponse diag(Integer configured, Integer actual) {
        DiagnosticResponse r = new DiagnosticResponse();
        r.setProvider(DiagnosticResponse.ProviderEnum.LOCAL);
        r.setChatOk(true);
        r.setEmbedOk(true);
        r.setChatLatencyMs(0);
        r.setEmbedLatencyMs(0);
        r.setChatModel("x");
        r.setEmbedModel("y");
        r.setEmbedDimensionsConfigured(configured);
        r.setEmbedDimensionsActual(actual);
        return r;
    }

    @Test
    void run_bootSucceeds_whenConfiguredAndActualMatchExpected() throws Exception {
        when(client.diagnostic()).thenReturn(diag(768, 768));
        EmbeddingDimensionCheck check = new EmbeddingDimensionCheck(client, 768);
        check.run(null);
    }

    @Test
    void run_bootFails_whenConfiguredDoesNotMatchExpected() {
        when(client.diagnostic()).thenReturn(diag(1536, 1536));
        EmbeddingDimensionCheck check = new EmbeddingDimensionCheck(client, 768);
        assertThatThrownBy(() -> check.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768")
                .hasMessageContaining("1536")
                .hasMessageContaining("EMBEDDING_DIMENSIONS");
    }

    @Test
    void run_bootFails_whenActualDoesNotMatchExpected() {
        when(client.diagnostic()).thenReturn(diag(768, 1024));
        EmbeddingDimensionCheck check = new EmbeddingDimensionCheck(client, 768);
        assertThatThrownBy(() -> check.run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("768")
                .hasMessageContaining("1024")
                .hasMessageContaining("OPENAI_EMBED_MODEL");
    }

    @Test
    void run_succeedsWithWarn_whenActualIsNull() throws Exception {
        when(client.diagnostic()).thenReturn(diag(768, null));
        EmbeddingDimensionCheck check = new EmbeddingDimensionCheck(client, 768);
        check.run(null);
        // Behavioural: no exception. Log assertions are optional; we rely on contract.
    }
}

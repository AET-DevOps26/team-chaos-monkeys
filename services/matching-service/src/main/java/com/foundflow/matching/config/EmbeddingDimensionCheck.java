package com.foundflow.matching.config;

import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.DiagnosticResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class EmbeddingDimensionCheck implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingDimensionCheck.class);

    private final GenaiClient genaiClient;
    private final int expectedDim;

    public EmbeddingDimensionCheck(GenaiClient genaiClient,
                                   @Value("${foundflow.matching.embedding-dim}") int expectedDim) {
        this.genaiClient = genaiClient;
        this.expectedDim = expectedDim;
    }

    @Override
    public void run(ApplicationArguments args) {
        DiagnosticResponse d = genaiClient.diagnostic();
        Integer configured = d.getEmbedDimensionsConfigured();
        Integer actual = d.getEmbedDimensionsActual();

        if (!Integer.valueOf(expectedDim).equals(configured)) {
            throw new IllegalStateException(
                    "Embedding-dim mismatch at startup: matching expects " + expectedDim
                            + " but genai-service is configured for " + configured + ". "
                            + "Set EMBEDDING_DIMENSIONS to the same value on both services.");
        }
        if (actual != null && !Integer.valueOf(expectedDim).equals(actual)) {
            throw new IllegalStateException(
                    "Embedding-dim mismatch at startup: matching expects " + expectedDim
                            + " but genai-service is actually producing " + actual + ". "
                            + "Check OPENAI_EMBED_MODEL / OLLAMA_EMBED_MODEL.");
        }
        if (actual == null) {
            log.warn("genai-service probe hasn't completed; ingest-time assertion will catch drift");
        } else {
            log.info("Embedding dim check passed: {} dims (provider={})", actual, d.getProvider());
        }
    }
}

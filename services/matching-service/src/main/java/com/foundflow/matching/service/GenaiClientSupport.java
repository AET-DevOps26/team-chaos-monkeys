package com.foundflow.matching.service;

import com.foundflow.genai.client.model.EmbedResponse;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;

/** Shared helpers for the synchronous genai-service interactions (embed / verify / answer). */
final class GenaiClientSupport {

    private GenaiClientSupport() {
    }

    /** Classifies a genai-call failure into a stable, low-cardinality metric/log reason. */
    static String classify(Throwable t) {
        if (t instanceof HttpServerErrorException.GatewayTimeout) return "timeout";
        if (t instanceof HttpServerErrorException) return "upstream_5xx";
        if (t instanceof HttpClientErrorException.TooManyRequests) return "throttled";
        if (t instanceof HttpClientErrorException) return "contract_error";
        if (t instanceof RejectedExecutionException) return "executor_full";
        if (t instanceof java.net.SocketTimeoutException
                || t instanceof ResourceAccessException) return "timeout";
        return "unexpected";
    }

    /** Extracts the first embedding vector from a genai {@code /embed} response as a primitive array. */
    static float[] toFloatArray(EmbedResponse response) {
        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("GenAI /embed returned no embeddings.");
        }
        List<Float> first = response.getEmbeddings().get(0);
        float[] vector = new float[first.size()];
        for (int i = 0; i < first.size(); i++) {
            vector[i] = first.get(i);
        }
        return vector;
    }
}

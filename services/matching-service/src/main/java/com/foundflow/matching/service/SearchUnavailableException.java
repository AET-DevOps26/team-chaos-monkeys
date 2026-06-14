package com.foundflow.matching.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a staff search cannot be served because the query could not be embedded
 * (genai {@code /embed} down/timeout) — retrieval is impossible without a query vector.
 */
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class SearchUnavailableException extends RuntimeException {

    public SearchUnavailableException(String message) {
        super(message);
    }
}

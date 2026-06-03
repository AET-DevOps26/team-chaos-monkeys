package com.foundflow.matching.service;

public class EmbeddingDimensionMismatchException extends RuntimeException {
    public EmbeddingDimensionMismatchException(String message) {
        super(message);
    }
}

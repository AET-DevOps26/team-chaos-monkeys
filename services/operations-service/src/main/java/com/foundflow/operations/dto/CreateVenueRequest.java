package com.foundflow.operations.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVenueRequest(
        @NotBlank
        String name,

        String tone,

        @NotBlank
        String defaultLanguage
) {
}
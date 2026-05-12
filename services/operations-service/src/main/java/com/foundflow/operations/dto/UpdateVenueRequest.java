package com.foundflow.operations.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateVenueRequest(
        @NotBlank
        String name,

        String tone,

        @NotBlank
        String defaultLanguage
) {
}
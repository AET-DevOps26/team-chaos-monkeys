package com.foundflow.matching.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreatePublicMatchLinkRequest(
        @NotBlank
        @Email
        String email
) {
}

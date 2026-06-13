package com.foundflow.matching.dto;

import jakarta.validation.constraints.Email;

public record CreatePublicMatchLinkRequest(
        @Email
        String email
) {
}

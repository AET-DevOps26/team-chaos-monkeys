package com.foundflow.auth.dto;

import com.foundflow.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        Role role
) {
}
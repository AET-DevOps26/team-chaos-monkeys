package com.foundflow.auth.dto;

import com.foundflow.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

public record CreateUserRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        Role role
) {
}
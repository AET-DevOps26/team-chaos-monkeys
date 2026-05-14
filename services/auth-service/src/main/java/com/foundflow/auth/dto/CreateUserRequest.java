package com.foundflow.auth.dto;

import com.foundflow.auth.domain.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Email
        String email,

        @NotNull
        Role role,

        @NotBlank
        @Size(min = 8)
        String password
) {
}
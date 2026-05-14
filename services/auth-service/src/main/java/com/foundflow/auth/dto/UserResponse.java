package com.foundflow.auth.dto;

import com.foundflow.auth.domain.Role;

import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        Role role
) {
}
package com.foundflow.lostitem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record CreateLostReportRequest(
        String photoKey,

        @NotBlank
        String description,

        @NotNull
        LocalDateTime lostAt,

        String location,

        @NotBlank
        @Email
        String contactEmail,

        @Valid
        ItemAttributesDto attributes
) {
}
package com.foundflow.lostitem.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public record CreateLostReportRequest(
        @NotBlank
        @Size(max = 2000)
        String description,

        @NotNull
        LocalDateTime lostAt,

        @Size(max = 255)
        String location,

        UUID venueId,

        @NotBlank
        @Email
        @Size(max = 255)
        String contactEmail,

        @Valid
        ItemAttributesDto attributes
) {
}

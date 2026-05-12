package com.foundflow.lostitem.dto;

import com.foundflow.lostitem.domain.ReportStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record UpdateLostReportRequest(
        String photoKey,

        @NotBlank
        String description,

        @NotNull
        LocalDateTime lostAt,

        String location,

        @NotNull
        ReportStatus status,

        @NotBlank
        @Email
        String contactEmail,

        @Valid
        ItemAttributesDto attributes
) {
}
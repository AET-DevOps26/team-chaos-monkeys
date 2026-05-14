package com.foundflow.lostitem.dto;

import com.foundflow.lostitem.domain.ReportStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record LostReportResponse(
        UUID id,
        String photoKey,
        String description,
        LocalDateTime lostAt,
        String location,
        ReportStatus status,
        String contactEmail,
        ItemAttributesDto attributes
) {
}
package com.foundflow.lostitem.dto;

import com.foundflow.lostitem.domain.ReportStatus;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.UUID;

public record LostReportResponse(
        UUID id,
        String photoKey,
        URI photoUrl,
        String description,
        LocalDateTime lostAt,
        String location,
        ReportStatus status,
        UUID venueId,
        String contactEmail,
        ItemAttributesDto attributes
) {
    public LostReportResponse(
            UUID id,
            String photoKey,
            String description,
            LocalDateTime lostAt,
            String location,
            ReportStatus status,
            UUID venueId,
            String contactEmail,
            ItemAttributesDto attributes
    ) {
        this(
                id,
                photoKey,
                null,
                description,
                lostAt,
                location,
                status,
                venueId,
                contactEmail,
                attributes
        );
    }
}

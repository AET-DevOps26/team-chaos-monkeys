package com.foundflow.events;

import java.time.Instant;
import java.util.UUID;

/**
 * Emitted by matching-service to drive a lost report's status as its match situation changes:
 * MATCHED once a guest is reached out to, OPEN again if the only match is rejected. lost-item-service
 * owns the actual transition guards. {@code status} is a {@code ReportStatus} name (that enum lives in
 * lost-item-service, so it travels as a String).
 */
public record LostReportStatusChangeRequestedEvent(
        UUID eventId,
        Instant occurredAt,
        UUID lostReportId,
        UUID venueId,
        String status
) {
    public static final String MATCHED = "MATCHED";
    public static final String OPEN = "OPEN";
}

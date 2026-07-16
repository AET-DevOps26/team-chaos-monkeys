package com.foundflow.lostitem.service;

import java.util.UUID;

/**
 * Internal application event: a lost report row has been committed and is ready
 * for off-request-path GenAI enrichment. Consumed by {@link IntakeEnrichmentService}
 * after the intake transaction commits.
 */
public record LostReportPersistedEvent(UUID reportId) {
}

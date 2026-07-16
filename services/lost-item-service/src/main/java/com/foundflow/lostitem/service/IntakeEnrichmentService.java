package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.messaging.LostReportEventPublisher;
import com.foundflow.lostitem.repository.LostReportRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Keeps GenAI attribute extraction off the intake request path.
 *
 * <p>Guest {@code POST /api/lost-items} used to block on a synchronous GenAI call
 * while holding a DB connection (up to the 15s read-timeout). The create path now
 * persists the report, returns immediately, and fires a {@link LostReportPersistedEvent};
 * {@link #onLostReportPersisted} runs after that transaction commits, on a bounded
 * executor, enriches best-effort and then publishes the {@code lost-report.created.v1}
 * domain event so matching still receives the extracted category.
 */
@Service
public class IntakeEnrichmentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntakeEnrichmentService.class);

    private final LostReportRepository lostReportRepository;
    private final AttributeExtractionService attributeExtractionService;
    private final LostReportEventPublisher eventPublisher;

    public IntakeEnrichmentService(
            LostReportRepository lostReportRepository,
            AttributeExtractionService attributeExtractionService,
            LostReportEventPublisher eventPublisher
    ) {
        this.lostReportRepository = lostReportRepository;
        this.attributeExtractionService = attributeExtractionService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Enriches a freshly created report off the request thread, then publishes the
     * created domain event. Runs after the intake transaction commits (so the row is
     * visible) in its own transaction on the {@code intakeEnrichmentExecutor}. The
     * event is published even when extraction adds nothing, so matching always runs.
     */
    @Async("intakeEnrichmentExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onLostReportPersisted(LostReportPersistedEvent event) {
        lostReportRepository.findById(event.reportId()).ifPresentOrElse(
                report -> {
                    enrich(report);
                    eventPublisher.publishLostReportCreated(report);
                },
                () -> LOGGER.warn(
                        "Skipping enrichment for lostReport={} — not found after commit.",
                        event.reportId()
                )
        );
    }

    /**
     * Best-effort GenAI enrichment when the report carries no meaningful attributes.
     * Runs for text-only reports too: the extraction service builds a description-only
     * request when the report has no photo, so a photo-less report like "purple shirt"
     * still gets a category and a generated description for the matching embedding.
     * Extraction failures are swallowed upstream, so this never throws on GenAI errors.
     */
    @Transactional
    public void enrich(LostReport report) {
        if (hasMeaningfulAttributes(report.getAttributes())) {
            return;
        }

        attributeExtractionService.extractWithLocation(report.getDescription(), report.getPhotoKey())
                .ifPresent(extracted -> {
                    report.setAttributes(extracted.attributes());
                    // Only fill in a location the guest didn't type — never clobber theirs.
                    if (extracted.location() != null && !hasText(report.getLocation())) {
                        report.setLocation(extracted.location());
                    }
                    lostReportRepository.save(report);
                });
    }

    private boolean hasMeaningfulAttributes(ItemAttributes attributes) {
        if (attributes == null) {
            return false;
        }

        return hasText(attributes.getCategory())
                || hasText(attributes.getDescription())
                || hasText(attributes.getBrand())
                || hasText(attributes.getColor())
                || (attributes.getMarks() != null && attributes.getMarks().stream().anyMatch(this::hasText));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

package com.foundflow.lostitem.service;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.genai.client.AttributeExtractionService;
import com.foundflow.genai.client.ExtractionResult;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.messaging.LostReportEventPublisher;
import com.foundflow.lostitem.repository.LostReportRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IntakeEnrichmentServiceTest {

    @Mock
    private LostReportRepository lostReportRepository;

    @Mock
    private AttributeExtractionService attributeExtractionService;

    @Mock
    private LostReportEventPublisher eventPublisher;

    @Test
    void onLostReportPersisted_shouldEnrichAndPublishCreated() {
        IntakeEnrichmentService service = service();
        UUID id = UUID.randomUUID();
        LostReport report = report("purple shirt", "near the cloakroom", emptyAttributes());
        ItemAttributes extracted = new ItemAttributes("CLOTHING", "purple cotton shirt", null, "purple", List.of());

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(attributeExtractionService.extractWithLocation("purple shirt", null))
                .thenReturn(Optional.of(new ExtractionResult(extracted, "the bar")));

        service.onLostReportPersisted(new LostReportPersistedEvent(id));

        assertEquals("CLOTHING", report.getAttributes().getCategory());
        // Guest typed a location, so the extracted one must not overwrite it.
        assertEquals("near the cloakroom", report.getLocation());
        verify(lostReportRepository).save(report);
        verify(eventPublisher).publishLostReportCreated(report);
    }

    @Test
    void onLostReportPersisted_shouldPublishCreatedEvenWhenExtractionAddsNothing() {
        IntakeEnrichmentService service = service();
        UUID id = UUID.randomUUID();
        LostReport report = report("vague thing", null, emptyAttributes());

        when(lostReportRepository.findById(id)).thenReturn(Optional.of(report));
        when(attributeExtractionService.extractWithLocation(any(), any()))
                .thenReturn(Optional.empty());

        service.onLostReportPersisted(new LostReportPersistedEvent(id));

        // Best-effort: no attributes added, but matching must still run.
        verify(lostReportRepository, never()).save(any());
        verify(eventPublisher).publishLostReportCreated(report);
    }

    @Test
    void onLostReportPersisted_shouldSkipWhenReportMissing() {
        IntakeEnrichmentService service = service();
        UUID id = UUID.randomUUID();
        when(lostReportRepository.findById(id)).thenReturn(Optional.empty());

        service.onLostReportPersisted(new LostReportPersistedEvent(id));

        verifyNoInteractions(attributeExtractionService);
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void enrich_shouldSkipExtractionWhenAttributesAlreadyPresent() {
        IntakeEnrichmentService service = service();
        LostReport report = report("black nike backpack", "stage 2",
                new ItemAttributes("Bag", null, "Nike", "Black", List.of()));

        service.enrich(report);

        verifyNoInteractions(attributeExtractionService);
        verify(lostReportRepository, never()).save(any());
    }

    @Test
    void enrich_shouldPersistExtractedLocationWhenGuestSuppliedNone() {
        IntakeEnrichmentService service = service();
        LostReport report = report("left my purple sunglasses at the bar", null, emptyAttributes());
        ItemAttributes extracted = new ItemAttributes("ACCESSORIES", "purple sunglasses", null, "purple", List.of());

        when(attributeExtractionService.extractWithLocation(any(), any()))
                .thenReturn(Optional.of(new ExtractionResult(extracted, "bar")));

        service.enrich(report);

        assertEquals("bar", report.getLocation());
        assertEquals("ACCESSORIES", report.getAttributes().getCategory());
        verify(lostReportRepository).save(report);
    }

    private IntakeEnrichmentService service() {
        return new IntakeEnrichmentService(lostReportRepository, attributeExtractionService, eventPublisher);
    }

    private ItemAttributes emptyAttributes() {
        return new ItemAttributes(null, null, null, null, List.of());
    }

    private LostReport report(String description, String location, ItemAttributes attributes) {
        return new LostReport(
                null,
                description,
                LocalDateTime.of(2026, 5, 12, 14, 30),
                location,
                ReportStatus.OPEN,
                UUID.randomUUID(),
                "person@example.com",
                attributes
        );
    }
}

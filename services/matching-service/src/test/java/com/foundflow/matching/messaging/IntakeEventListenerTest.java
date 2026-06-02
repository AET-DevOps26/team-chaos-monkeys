package com.foundflow.matching.messaging;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.matching.service.CandidateMatchingService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IntakeEventListenerTest {

    @Test
    void onLostReportCreated_shouldTriggerCandidateSearch() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        LostReportCreatedEvent event = new LostReportCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lost-reports/2026/05/photo.jpg",
                "Black backpack",
                Instant.parse("2026-05-24T12:30:00Z"),
                "Front desk",
                "OPEN",
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );

        listener.onLostReportCreated(event);

        verify(candidateMatchingService).findCandidatesForLostReport(event);
    }

    @Test
    void onFoundItemLogged_shouldTriggerCandidateSearch() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        FoundItemLoggedEvent event = new FoundItemLoggedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "found-items/2026/05/photo.jpg",
                "Black backpack",
                Instant.parse("2026-05-24T12:30:00Z"),
                "Front desk",
                "STORED",
                UUID.randomUUID(),
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );

        listener.onFoundItemLogged(event);

        verify(candidateMatchingService).findCandidatesForFoundItem(event);
    }

    @Test
    void onLostReportUpdated_shouldTriggerCandidateSearch() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        LostReportUpdatedEvent event = new LostReportUpdatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lost-reports/2026/05/photo.jpg",
                "Updated black backpack",
                Instant.parse("2026-05-24T12:45:00Z"),
                "Updated front desk",
                "OPEN",
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );

        listener.onLostReportUpdated(event);

        verify(candidateMatchingService).findCandidatesForLostReportUpdate(event);
    }

    @Test
    void onFoundItemUpdated_shouldTriggerCandidateSearch() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        FoundItemUpdatedEvent event = new FoundItemUpdatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "found-items/2026/05/photo.jpg",
                "Updated black backpack",
                Instant.parse("2026-05-24T12:45:00Z"),
                "Updated front desk",
                "STORED",
                UUID.randomUUID(),
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );

        listener.onFoundItemUpdated(event);

        verify(candidateMatchingService).findCandidatesForFoundItemUpdate(event);
    }

    @Test
    void nonRetryableGenAiError_shouldBeSkippedWithoutRabbitRedelivery() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        LostReportCreatedEvent event = lostReportCreatedEvent();
        doThrow(new RestClientException("bad request"))
                .when(candidateMatchingService)
                .findCandidatesForLostReport(event);

        assertDoesNotThrow(() -> listener.onLostReportCreated(event));

        verify(candidateMatchingService).findCandidatesForLostReport(event);
    }

    @Test
    void retryableGenAiError_shouldBeSkippedWithoutRabbitRedelivery() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, new SimpleMeterRegistry());
        LostReportCreatedEvent event = lostReportCreatedEvent();
        ResourceAccessException exception = new ResourceAccessException("timeout");
        doThrow(exception)
                .when(candidateMatchingService)
                .findCandidatesForLostReport(event);

        assertDoesNotThrow(() -> listener.onLostReportCreated(event));

        verify(candidateMatchingService).findCandidatesForLostReport(event);
    }

    @Test
    void skippedSearch_shouldIncrementCounterTaggedByEventType() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService, registry);
        LostReportCreatedEvent event = lostReportCreatedEvent();
        doThrow(new RestClientException("genai unavailable"))
                .when(candidateMatchingService)
                .findCandidatesForLostReport(event);

        listener.onLostReportCreated(event);

        assertEquals(
                1.0,
                registry.counter("matching.candidate_search.skipped", "event_type", "LostReportCreated").count()
        );
    }

    private LostReportCreatedEvent lostReportCreatedEvent() {
        return new LostReportCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lost-reports/2026/05/photo.jpg",
                "Black backpack",
                Instant.parse("2026-05-24T12:30:00Z"),
                "Front desk",
                "OPEN",
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );
    }
}

package com.foundflow.matching.messaging;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.matching.service.CandidateMatchingService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class IntakeEventListenerTest {

    @Test
    void onLostReportCreated_shouldTriggerCandidateSearch() {
        CandidateMatchingService candidateMatchingService = mock(CandidateMatchingService.class);
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService);
        LostReportCreatedEvent event = new LostReportCreatedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lost-reports/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 12, 30),
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
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService);
        FoundItemLoggedEvent event = new FoundItemLoggedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "found-items/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 12, 30),
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
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService);
        LostReportUpdatedEvent event = new LostReportUpdatedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "lost-reports/2026/05/photo.jpg",
                "Updated black backpack",
                LocalDateTime.of(2026, 5, 24, 12, 45),
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
        IntakeEventListener listener = new IntakeEventListener(candidateMatchingService);
        FoundItemUpdatedEvent event = new FoundItemUpdatedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "found-items/2026/05/photo.jpg",
                "Updated black backpack",
                LocalDateTime.of(2026, 5, 24, 12, 45),
                "Updated front desk",
                "STORED",
                UUID.randomUUID(),
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag"))
        );

        listener.onFoundItemUpdated(event);

        verify(candidateMatchingService).findCandidatesForFoundItemUpdate(event);
    }
}

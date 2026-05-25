package com.foundflow.lostitem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LostReportEventPublisherTest {

    @Test
    void publishLostReportCreated_shouldSendDomainEventWithoutContactEmail() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        LostReportEventPublisher publisher = new LostReportEventPublisher(rabbitTemplate);
        LostReport lostReport = lostReport();

        publisher.publishLostReportCreated(lostReport);

        ArgumentCaptor<LostReportCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(LostReportCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_CREATED),
                eventCaptor.capture()
        );

        LostReportCreatedEvent event = eventCaptor.getValue();
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(lostReport.getId(), event.lostReportId());
        assertEquals(lostReport.getVenueId(), event.venueId());
        assertEquals("lost-reports/2026/05/photo.jpg", event.photoKey());
        assertEquals("Black backpack", event.description());
        assertEquals(Instant.parse("2026-05-24T11:30:00Z"), event.lostAt());
        assertEquals("Front desk", event.location());
        assertEquals("OPEN", event.status());
        assertEquals("Bag", event.attributes().category());
        assertEquals("Nike", event.attributes().brand());
        assertEquals("Black", event.attributes().color());
        assertEquals(List.of("red tag"), event.attributes().marks());
    }

    @Test
    void publishLostReportUpdated_shouldSendDomainEventWithoutContactEmail() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        LostReportEventPublisher publisher = new LostReportEventPublisher(rabbitTemplate);
        LostReport lostReport = lostReport();

        publisher.publishLostReportUpdated(lostReport);

        ArgumentCaptor<LostReportUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(LostReportUpdatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_UPDATED),
                eventCaptor.capture()
        );

        LostReportUpdatedEvent event = eventCaptor.getValue();
        assertEquals(lostReport.getId(), event.lostReportId());
        assertEquals(lostReport.getVenueId(), event.venueId());
        assertEquals("lost-reports/2026/05/photo.jpg", event.photoKey());
        assertEquals("Black backpack", event.description());
        assertEquals(Instant.parse("2026-05-24T11:30:00Z"), event.lostAt());
        assertEquals("Front desk", event.location());
        assertEquals("OPEN", event.status());
        assertEquals("Bag", event.attributes().category());
    }

    private LostReport lostReport() {
        return new LostReport(
                "lost-reports/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 11, 30),
                "Front desk",
                ReportStatus.OPEN,
                UUID.randomUUID(),
                "guest@example.com",
                new ItemAttributes("Bag", "Nike", "Black", List.of("red tag"))
        );
    }
}

package com.foundflow.lostitem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.lostitem.domain.LostReport;
import com.foundflow.lostitem.domain.ReportStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LostReportEventPublisherTest {

    @Test
    void publishLostReportCreated_shouldSendVersionedDomainEventWithoutContactEmail() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        LostReportEventPublisher publisher = new LostReportEventPublisher(rabbitTemplate);
        UUID venueId = UUID.randomUUID();
        LostReport lostReport = new LostReport(
                "lost-reports/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 11, 30),
                "Front desk",
                ReportStatus.OPEN,
                venueId,
                "guest@example.com",
                new ItemAttributes("Bag", "Nike", "Black", List.of("red tag"))
        );

        publisher.publishLostReportCreated(lostReport);

        ArgumentCaptor<LostReportCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(LostReportCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.LOST_REPORT_CREATED),
                eventCaptor.capture()
        );

        LostReportCreatedEvent event = eventCaptor.getValue();
        assertEquals(1, event.version());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(lostReport.getId(), event.lostReportId());
        assertEquals(venueId, event.venueId());
        assertEquals("lost-reports/2026/05/photo.jpg", event.photoKey());
        assertEquals("OPEN", event.status());
        assertEquals("Bag", event.attributes().category());
    }
}

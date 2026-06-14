package com.foundflow.operations.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.VenueDeletedEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VenueEventPublisherTest {

    @Test
    void publishVenueDeleted_shouldSendDomainEvent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        VenueEventPublisher publisher = new VenueEventPublisher(rabbitTemplate);
        UUID venueId = UUID.randomUUID();

        publisher.publishVenueDeleted(venueId);

        ArgumentCaptor<VenueDeletedEvent> eventCaptor =
                ArgumentCaptor.forClass(VenueDeletedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.VENUE_DELETED),
                eventCaptor.capture()
        );

        VenueDeletedEvent event = eventCaptor.getValue();
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(venueId, event.venueId());
    }
}

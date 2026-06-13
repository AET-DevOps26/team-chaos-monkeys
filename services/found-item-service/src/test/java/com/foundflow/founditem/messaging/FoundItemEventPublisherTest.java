package com.foundflow.founditem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemCreatedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.founditem.domain.ItemStatus;
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

class FoundItemEventPublisherTest {

    @Test
    void publishFoundItemCreated_shouldSendDomainEvent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        FoundItemEventPublisher publisher = new FoundItemEventPublisher(rabbitTemplate);
        FoundItem foundItem = foundItem();

        publisher.publishFoundItemCreated(foundItem);

        ArgumentCaptor<FoundItemCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(FoundItemCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.FOUND_ITEM_CREATED),
                eventCaptor.capture()
        );

        FoundItemCreatedEvent event = eventCaptor.getValue();
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(foundItem.getId(), event.foundItemId());
        assertEquals(foundItem.getVenueId(), event.venueId());
        assertEquals(foundItem.getReporterId(), event.reporterId());
        assertEquals("found-items/2026/05/photo.jpg", event.photoKey());
        assertEquals("Black backpack", event.intakeText());
        assertEquals(Instant.parse("2026-05-24T11:30:00Z"), event.foundAt());
        assertEquals("Front desk", event.location());
        assertEquals("STORED", event.status());
        assertEquals("Bag", event.attributes().category());
        assertEquals("Nike", event.attributes().brand());
        assertEquals("Black", event.attributes().color());
        assertEquals(List.of("red tag"), event.attributes().marks());
    }

    @Test
    void publishFoundItemUpdated_shouldSendDomainEvent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        FoundItemEventPublisher publisher = new FoundItemEventPublisher(rabbitTemplate);
        FoundItem foundItem = foundItem();

        publisher.publishFoundItemUpdated(foundItem);

        ArgumentCaptor<FoundItemUpdatedEvent> eventCaptor =
                ArgumentCaptor.forClass(FoundItemUpdatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.FOUND_ITEM_UPDATED),
                eventCaptor.capture()
        );

        FoundItemUpdatedEvent event = eventCaptor.getValue();
        assertEquals(foundItem.getId(), event.foundItemId());
        assertEquals(foundItem.getVenueId(), event.venueId());
        assertEquals(foundItem.getReporterId(), event.reporterId());
        assertEquals("found-items/2026/05/photo.jpg", event.photoKey());
        assertEquals("Black backpack", event.intakeText());
        assertEquals(Instant.parse("2026-05-24T11:30:00Z"), event.foundAt());
        assertEquals("Front desk", event.location());
        assertEquals("STORED", event.status());
        assertEquals("Bag", event.attributes().category());
    }

    private FoundItem foundItem() {
        return new FoundItem(
                "found-items/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 11, 30),
                "Front desk",
                ItemStatus.STORED,
                UUID.randomUUID(),
                UUID.randomUUID(),
                new ItemAttributes("Bag", "Nike", "Black", List.of("red tag"))
        );
    }
}

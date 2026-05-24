package com.foundflow.founditem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.founditem.domain.FoundItem;
import com.foundflow.founditem.domain.ItemStatus;
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

class FoundItemEventPublisherTest {

    @Test
    void publishFoundItemLogged_shouldSendVersionedDomainEvent() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        FoundItemEventPublisher publisher = new FoundItemEventPublisher(rabbitTemplate);
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        FoundItem foundItem = new FoundItem(
                "found-items/2026/05/photo.jpg",
                "Black backpack",
                LocalDateTime.of(2026, 5, 24, 11, 30),
                "Front desk",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributes("Bag", "Nike", "Black", List.of("red tag"))
        );

        publisher.publishFoundItemLogged(foundItem);

        ArgumentCaptor<FoundItemLoggedEvent> eventCaptor =
                ArgumentCaptor.forClass(FoundItemLoggedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.FOUND_ITEM_LOGGED),
                eventCaptor.capture()
        );

        FoundItemLoggedEvent event = eventCaptor.getValue();
        assertEquals(1, event.version());
        assertNotNull(event.eventId());
        assertNotNull(event.occurredAt());
        assertEquals(foundItem.getId(), event.foundItemId());
        assertEquals(venueId, event.venueId());
        assertEquals(reporterId, event.reporterId());
        assertEquals("found-items/2026/05/photo.jpg", event.photoKey());
        assertEquals("STORED", event.status());
        assertEquals("Bag", event.attributes().category());
    }
}

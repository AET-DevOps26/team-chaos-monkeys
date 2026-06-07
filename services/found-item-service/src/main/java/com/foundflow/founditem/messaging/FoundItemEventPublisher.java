package com.foundflow.founditem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.founditem.domain.FoundItem;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class FoundItemEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public FoundItemEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishFoundItemLogged(FoundItem foundItem) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.FOUND_ITEM_LOGGED,
                toEvent(foundItem)
        );
    }

    public void publishFoundItemUpdated(FoundItem foundItem) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.FOUND_ITEM_UPDATED,
                toUpdatedEvent(foundItem)
        );
    }

    private FoundItemLoggedEvent toEvent(FoundItem foundItem) {
        return new FoundItemLoggedEvent(
                UUID.randomUUID(),
                Instant.now(),
                foundItem.getId(),
                foundItem.getVenueId(),
                foundItem.getPhotoKey(),
                foundItem.getIntakeText(),
                toInstant(foundItem.getFoundAt()),
                foundItem.getLocation(),
                foundItem.getStatus().name(),
                foundItem.getReporterId(),
                toPayload(foundItem.getAttributes())
        );
    }

    private FoundItemUpdatedEvent toUpdatedEvent(FoundItem foundItem) {
        return new FoundItemUpdatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                foundItem.getId(),
                foundItem.getVenueId(),
                foundItem.getPhotoKey(),
                foundItem.getIntakeText(),
                toInstant(foundItem.getFoundAt()),
                foundItem.getLocation(),
                foundItem.getStatus().name(),
                foundItem.getReporterId(),
                toPayload(foundItem.getAttributes())
        );
    }

    private ItemAttributesPayload toPayload(ItemAttributes attributes) {
        if (attributes == null) {
            return null;
        }

        return new ItemAttributesPayload(
                attributes.getCategory(),
                attributes.getBrand(),
                attributes.getColor(),
                attributes.getMarks()
        );
    }

    private Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}

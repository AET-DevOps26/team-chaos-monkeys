package com.foundflow.operations.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.VenueDeletedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class VenueEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public VenueEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishVenueDeleted(UUID venueId) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.VENUE_DELETED,
                new VenueDeletedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        venueId
                )
        );
    }
}

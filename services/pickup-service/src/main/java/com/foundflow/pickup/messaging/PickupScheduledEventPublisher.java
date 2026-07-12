package com.foundflow.pickup.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PickupScheduledEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PickupScheduledEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PickupScheduledEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPickupScheduled(UUID pickupId, UUID matchId, UUID venueId) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.PICKUP_SCHEDULED,
                new PickupScheduledEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        pickupId,
                        matchId,
                        venueId
                )
        );
    }
}

package com.foundflow.pickup.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PickupConfirmationRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class PickupConfirmationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public PickupConfirmationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishPickupConfirmationRequested(UUID pickupId, UUID matchId, String recipient, UUID venueId) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.PICKUP_CONFIRMATION_REQUESTED,
                new PickupConfirmationRequestedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        pickupId,
                        matchId,
                        recipient,
                        venueId
                )
        );
    }
}

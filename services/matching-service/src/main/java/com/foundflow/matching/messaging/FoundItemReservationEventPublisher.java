package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemReservationRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class FoundItemReservationEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public FoundItemReservationEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishReservationRequested(UUID foundItemId, UUID venueId) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTED,
                new FoundItemReservationRequestedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        foundItemId,
                        venueId
                )
        );
    }
}

package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchInviteRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MatchInviteEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MatchInviteEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchInviteRequested(UUID matchId, String recipient, UUID venueId) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.MATCH_INVITE_REQUESTED,
                new MatchInviteRequestedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        matchId,
                        recipient,
                        venueId
                )
        );
    }
}

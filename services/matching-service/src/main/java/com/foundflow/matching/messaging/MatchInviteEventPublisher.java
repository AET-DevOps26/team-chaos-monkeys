package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchInviteRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
public class MatchInviteEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MatchInviteEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchInviteRequested(UUID matchId, String recipient, UUID venueId, String matchUrl) {
        publishAfterCommit(new MatchInviteRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                matchId,
                recipient,
                venueId,
                matchUrl
        ));
    }

    private void publishAfterCommit(MatchInviteRequestedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    private void send(MatchInviteRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.MATCH_INVITE_REQUESTED,
                event
        );
    }
}

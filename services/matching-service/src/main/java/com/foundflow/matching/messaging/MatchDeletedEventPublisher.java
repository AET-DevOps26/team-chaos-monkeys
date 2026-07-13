package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchDeletedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
public class MatchDeletedEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MatchDeletedEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchDeleted(UUID matchId, UUID venueId) {
        publishAfterCommit(
                FoundFlowEventRouting.MATCH_DELETED,
                new MatchDeletedEvent(UUID.randomUUID(), Instant.now(), matchId, venueId)
        );
    }

    private void publishAfterCommit(String routingKey, Object event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            send(routingKey, event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(routingKey, event);
            }
        });
    }

    private void send(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(FoundFlowEventRouting.EXCHANGE, routingKey, event);
    }
}

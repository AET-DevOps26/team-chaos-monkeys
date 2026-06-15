package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchCandidateCreatedEvent;
import com.foundflow.matching.domain.Match;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
public class MatchCandidateEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MatchCandidateEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchCandidateCreated(Match match) {
        publishAfterCommit(
                FoundFlowEventRouting.MATCH_CANDIDATE_CREATED,
                new MatchCandidateCreatedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        match.getId(),
                        match.getLostReportId(),
                        match.getFoundItemId(),
                        match.getVenueId(),
                        match.getRecipientEmail(),
                        match.getAttributeScore(),
                        match.getSemanticScore(),
                        match.getCombinedScore()
                )
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
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                routingKey,
                event
        );
    }
}

package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchCandidateCreatedEvent;
import com.foundflow.matching.domain.Match;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class MatchCandidateEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public MatchCandidateEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatchCandidateCreated(Match match) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.MATCH_CANDIDATE_CREATED,
                new MatchCandidateCreatedEvent(
                        UUID.randomUUID(),
                        1,
                        Instant.now(),
                        match.getId(),
                        match.getLostReportId(),
                        match.getFoundItemId(),
                        match.getVenueId(),
                        match.getAttributeScore(),
                        match.getSemanticScore(),
                        match.getCombinedScore()
                )
        );
    }
}

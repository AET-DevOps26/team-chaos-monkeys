package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchCandidateCreatedEvent;
import com.foundflow.matching.service.MatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class MatchCandidateAutoInviteListener {

    private static final Logger log = LoggerFactory.getLogger(MatchCandidateAutoInviteListener.class);

    private final MatchService matchService;
    private final float autoInviteThreshold;

    public MatchCandidateAutoInviteListener(
            MatchService matchService,
            @Value("${foundflow.matching.auto-invite-threshold:0.85}") float autoInviteThreshold
    ) {
        this.matchService = matchService;
        this.autoInviteThreshold = autoInviteThreshold;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE)
    public void onMatchCandidateCreated(MatchCandidateCreatedEvent event) {
        if (event.combinedScore() < autoInviteThreshold) {
            log.debug(
                    "Match candidate {} scored {} below auto-invite threshold {}.",
                    event.matchId(),
                    event.combinedScore(),
                    autoInviteThreshold
            );
            return;
        }

        matchService.createAutomaticPublicMatchLink(event.matchId(), event.recipientEmail())
                .ifPresentOrElse(
                        response -> log.info("Auto-invited guest for match {}.", event.matchId()),
                        () -> log.warn("Skipped auto-invite for match {}; no pending match or recipient email.",
                                event.matchId())
                );
    }
}

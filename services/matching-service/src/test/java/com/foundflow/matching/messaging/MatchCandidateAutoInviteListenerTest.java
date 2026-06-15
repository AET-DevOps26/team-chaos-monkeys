package com.foundflow.matching.messaging;

import com.foundflow.events.MatchCandidateCreatedEvent;
import com.foundflow.matching.dto.PublicMatchLinkResponse;
import com.foundflow.matching.service.MatchService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchCandidateAutoInviteListenerTest {

    @Test
    void aboveAutoInviteThreshold_createsPublicMatchLink() {
        MatchService matchService = mock(MatchService.class);
        MatchCandidateAutoInviteListener listener = new MatchCandidateAutoInviteListener(matchService, 0.85f);
        MatchCandidateCreatedEvent event = event(0.90f, "guest@example.com");
        when(matchService.createAutomaticPublicMatchLink(event.matchId(), event.recipientEmail()))
                .thenReturn(Optional.of(new PublicMatchLinkResponse(
                        "token",
                        "http://localhost:8080/api/matches/public/token",
                        "http://localhost:8080/api/pickups/public/token"
                )));

        listener.onMatchCandidateCreated(event);

        verify(matchService).createAutomaticPublicMatchLink(event.matchId(), "guest@example.com");
    }

    @Test
    void belowAutoInviteThreshold_doesNothing() {
        MatchService matchService = mock(MatchService.class);
        MatchCandidateAutoInviteListener listener = new MatchCandidateAutoInviteListener(matchService, 0.85f);
        MatchCandidateCreatedEvent event = event(0.84f, "guest@example.com");

        listener.onMatchCandidateCreated(event);

        verify(matchService, never()).createAutomaticPublicMatchLink(event.matchId(), event.recipientEmail());
    }

    private MatchCandidateCreatedEvent event(float combinedScore, String recipientEmail) {
        return new MatchCandidateCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                recipientEmail,
                1.0f,
                combinedScore,
                combinedScore
        );
    }
}

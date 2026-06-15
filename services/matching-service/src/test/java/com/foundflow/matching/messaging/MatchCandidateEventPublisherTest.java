package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchCandidateCreatedEvent;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class MatchCandidateEventPublisherTest {

    @Test
    void publishMatchCandidateCreated_shouldIncludeRecipientEmail() {
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
        MatchCandidateEventPublisher publisher = new MatchCandidateEventPublisher(rabbitTemplate);
        UUID matchId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = new Match(
                foundItemId,
                lostReportId,
                venueId,
                "guest@example.com",
                MatchStatus.PENDING,
                1.0f,
                0.9f,
                0.9f,
                LocalDateTime.now()
        );
        ReflectionTestUtils.setField(match, "id", matchId);

        publisher.publishMatchCandidateCreated(match);

        ArgumentCaptor<MatchCandidateCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(MatchCandidateCreatedEvent.class);
        verify(rabbitTemplate).convertAndSend(
                eq(FoundFlowEventRouting.EXCHANGE),
                eq(FoundFlowEventRouting.MATCH_CANDIDATE_CREATED),
                eventCaptor.capture()
        );
        assertThat(eventCaptor.getValue().matchId()).isEqualTo(matchId);
        assertThat(eventCaptor.getValue().recipientEmail()).isEqualTo("guest@example.com");
    }
}

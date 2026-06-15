package com.foundflow.matching.config;

import com.foundflow.events.FoundFlowEventRouting;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AmqpConfigTest {

    private final AmqpConfig config = new AmqpConfig();

    @Test
    void consumerQueues_shouldRouteRejectedMessagesToPerQueueDlq() {
        var queue = config.lostReportCreatedQueue();

        assertThat(queue.getName()).isEqualTo(FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE);
        assertThat(queue.getArguments())
                .containsEntry("x-dead-letter-exchange", FoundFlowEventRouting.DEAD_LETTER_EXCHANGE)
                .containsEntry(
                        "x-dead-letter-routing-key",
                        FoundFlowEventRouting.deadLetterRoutingKey(FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE)
                );
    }

    @Test
    void matchCandidateQueue_shouldBeBoundAsMatchingConsumerQueue() {
        var queue = config.matchCandidateCreatedQueue();
        var dlq = config.matchCandidateCreatedDlq();

        assertThat(queue.getName()).isEqualTo(FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE);
        assertThat(dlq.getName())
                .isEqualTo(FoundFlowEventRouting.deadLetterQueue(FoundFlowEventRouting.MATCHING_MATCH_CANDIDATES_QUEUE));
    }
}

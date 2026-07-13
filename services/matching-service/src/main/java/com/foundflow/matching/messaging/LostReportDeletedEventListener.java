package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportDeletedEvent;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class LostReportDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(LostReportDeletedEventListener.class);

    private final MatchRepository matchRepository;
    private final ItemEmbeddingRepository itemEmbeddingRepository;
    private final MatchDeletedEventPublisher matchDeletedEventPublisher;

    public LostReportDeletedEventListener(
            MatchRepository matchRepository,
            ItemEmbeddingRepository itemEmbeddingRepository,
            MatchDeletedEventPublisher matchDeletedEventPublisher
    ) {
        this.matchRepository = matchRepository;
        this.itemEmbeddingRepository = itemEmbeddingRepository;
        this.matchDeletedEventPublisher = matchDeletedEventPublisher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_LOST_REPORT_DELETES_QUEUE)
    @Transactional
    public void onLostReportDeleted(LostReportDeletedEvent event) {
        List<UUID> matchIds = matchRepository.findIdsByLostReportId(event.lostReportId());
        int deletedMatches = matchRepository.deleteByLostReportId(event.lostReportId());
        int deletedEmbeddings = itemEmbeddingRepository.deleteByItemTypeAndItemId(
                ItemType.LOST,
                event.lostReportId()
        );
        matchIds.forEach(matchId -> matchDeletedEventPublisher.publishMatchDeleted(matchId, event.venueId()));

        log.info(
                "Cleaned up matching state for LostReportDeleted event {} lostReport={} venue={} matches={} embeddings={}",
                event.eventId(),
                event.lostReportId(),
                event.venueId(),
                deletedMatches,
                deletedEmbeddings
        );
    }
}

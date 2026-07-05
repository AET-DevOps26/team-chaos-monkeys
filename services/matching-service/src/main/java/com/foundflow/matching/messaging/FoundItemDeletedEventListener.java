package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemDeletedEvent;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FoundItemDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(FoundItemDeletedEventListener.class);

    private final MatchRepository matchRepository;
    private final ItemEmbeddingRepository itemEmbeddingRepository;

    public FoundItemDeletedEventListener(
            MatchRepository matchRepository,
            ItemEmbeddingRepository itemEmbeddingRepository
    ) {
        this.matchRepository = matchRepository;
        this.itemEmbeddingRepository = itemEmbeddingRepository;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_FOUND_ITEM_DELETES_QUEUE)
    @Transactional
    public void onFoundItemDeleted(FoundItemDeletedEvent event) {
        int deletedMatches = matchRepository.deleteByFoundItemId(event.foundItemId());
        int deletedEmbeddings = itemEmbeddingRepository.deleteByItemTypeAndItemId(
                ItemType.FOUND,
                event.foundItemId()
        );

        log.info(
                "Cleaned up matching state for FoundItemDeleted event {} foundItem={} venue={} matches={} embeddings={}",
                event.eventId(),
                event.foundItemId(),
                event.venueId(),
                deletedMatches,
                deletedEmbeddings
        );
    }
}

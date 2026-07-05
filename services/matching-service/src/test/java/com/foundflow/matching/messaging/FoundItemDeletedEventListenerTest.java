package com.foundflow.matching.messaging;

import com.foundflow.events.FoundItemDeletedEvent;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class FoundItemDeletedEventListenerTest {

    @Test
    void onFoundItemDeleted_shouldDeleteMatchesAndEmbeddingForFoundItem() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        ItemEmbeddingRepository itemEmbeddingRepository = mock(ItemEmbeddingRepository.class);
        FoundItemDeletedEventListener listener = new FoundItemDeletedEventListener(
                matchRepository,
                itemEmbeddingRepository
        );
        UUID foundItemId = UUID.randomUUID();

        listener.onFoundItemDeleted(new FoundItemDeletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                foundItemId,
                UUID.randomUUID()
        ));

        verify(matchRepository).deleteByFoundItemId(foundItemId);
        verify(itemEmbeddingRepository).deleteByItemTypeAndItemId(ItemType.FOUND, foundItemId);
    }
}

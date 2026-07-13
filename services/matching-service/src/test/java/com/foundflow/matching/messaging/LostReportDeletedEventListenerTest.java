package com.foundflow.matching.messaging;

import com.foundflow.events.LostReportDeletedEvent;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LostReportDeletedEventListenerTest {

    @Test
    void onLostReportDeleted_shouldDeleteMatchesEmbeddingAndAnnounceEachRemovedMatch() {
        MatchRepository matchRepository = mock(MatchRepository.class);
        ItemEmbeddingRepository itemEmbeddingRepository = mock(ItemEmbeddingRepository.class);
        MatchDeletedEventPublisher matchDeletedEventPublisher = mock(MatchDeletedEventPublisher.class);
        LostReportDeletedEventListener listener = new LostReportDeletedEventListener(
                matchRepository,
                itemEmbeddingRepository,
                matchDeletedEventPublisher
        );
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        when(matchRepository.findIdsByLostReportId(lostReportId)).thenReturn(List.of(matchId));

        listener.onLostReportDeleted(new LostReportDeletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReportId,
                venueId
        ));

        verify(matchRepository).deleteByLostReportId(lostReportId);
        verify(itemEmbeddingRepository).deleteByItemTypeAndItemId(ItemType.LOST, lostReportId);
        verify(matchDeletedEventPublisher).publishMatchDeleted(matchId, venueId);
    }
}

package com.foundflow.pickup.messaging;

import com.foundflow.events.MatchDeletedEvent;
import com.foundflow.pickup.repository.PickupRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MatchDeletedEventListenerTest {

    @Test
    void onMatchDeleted_shouldDeletePickupsForMatch() {
        PickupRepository pickupRepository = mock(PickupRepository.class);
        MatchDeletedEventListener listener = new MatchDeletedEventListener(pickupRepository);
        UUID matchId = UUID.randomUUID();
        when(pickupRepository.deleteByMatchId(matchId)).thenReturn(1L);

        listener.onMatchDeleted(new MatchDeletedEvent(
                UUID.randomUUID(),
                Instant.now(),
                matchId,
                UUID.randomUUID()
        ));

        verify(pickupRepository).deleteByMatchId(matchId);
    }
}

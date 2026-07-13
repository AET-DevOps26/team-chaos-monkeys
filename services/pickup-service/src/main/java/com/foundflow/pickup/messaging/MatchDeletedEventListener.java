package com.foundflow.pickup.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchDeletedEvent;
import com.foundflow.pickup.repository.PickupRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MatchDeletedEventListener {

    private static final Logger log = LoggerFactory.getLogger(MatchDeletedEventListener.class);

    private final PickupRepository pickupRepository;

    public MatchDeletedEventListener(PickupRepository pickupRepository) {
        this.pickupRepository = pickupRepository;
    }

    // When a lost/found item is deleted, matching-service tears down its matches and emits
    // one MatchDeleted per match. Pickups key on matchId, so drop any pickup booked against
    // the now-gone match (issue #384). Idempotent: a redelivered event deletes 0 rows.
    @RabbitListener(queues = FoundFlowEventRouting.PICKUP_MATCH_DELETED_QUEUE)
    @Transactional
    public void onMatchDeleted(MatchDeletedEvent event) {
        long deleted = pickupRepository.deleteByMatchId(event.matchId());
        if (deleted > 0) {
            log.info(
                    "Removed {} pickup(s) for deleted match {} venue={} (event {})",
                    deleted,
                    event.matchId(),
                    event.venueId(),
                    event.eventId()
            );
        }
    }
}

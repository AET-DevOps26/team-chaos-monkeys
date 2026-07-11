package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PickupScheduledEvent;
import com.foundflow.matching.repository.MatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * When a guest books a pickup, retire the found item behind the match: matching-service
 * owns the match → foundItemId mapping, so it resolves the item and asks found-item-service
 * to reserve it. The resulting FoundItemUpdated(RESERVED) then drops the embedding from the
 * candidate pool (see {@code CandidateMatchingService#findCandidatesForFoundItemUpdate}).
 */
@Component
public class PickupScheduledEventListener {

    private static final Logger log = LoggerFactory.getLogger(PickupScheduledEventListener.class);

    private final MatchRepository matchRepository;
    private final FoundItemReservationEventPublisher reservationEventPublisher;

    public PickupScheduledEventListener(
            MatchRepository matchRepository,
            FoundItemReservationEventPublisher reservationEventPublisher
    ) {
        this.matchRepository = matchRepository;
        this.reservationEventPublisher = reservationEventPublisher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_PICKUP_SCHEDULED_QUEUE)
    public void onPickupScheduled(PickupScheduledEvent event) {
        matchRepository.findById(event.matchId()).ifPresentOrElse(
                match -> {
                    reservationEventPublisher.publishReservationRequested(
                            match.getFoundItemId(),
                            match.getVenueId()
                    );
                    log.info(
                            "Pickup scheduled for match {} — requested reservation of found item {}.",
                            event.matchId(),
                            match.getFoundItemId()
                    );
                },
                () -> log.warn(
                        "Pickup scheduled for unknown match {} — no found item to reserve.",
                        event.matchId()
                )
        );
    }
}

package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.matching.service.CandidateMatchingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class IntakeEventListener {

    private static final Logger log = LoggerFactory.getLogger(IntakeEventListener.class);

    private final CandidateMatchingService candidateMatchingService;

    public IntakeEventListener(CandidateMatchingService candidateMatchingService) {
        this.candidateMatchingService = candidateMatchingService;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE)
    public void onLostReportCreated(LostReportCreatedEvent event) {
        log.info(
                "Received LostReportCreated event {} for lostReport={} venue={}",
                event.eventId(),
                event.lostReportId(),
                event.venueId()
        );
        candidateMatchingService.findCandidatesForLostReport(event);
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE)
    public void onFoundItemLogged(FoundItemLoggedEvent event) {
        log.info(
                "Received FoundItemLogged event {} for foundItem={} venue={}",
                event.eventId(),
                event.foundItemId(),
                event.venueId()
        );
        candidateMatchingService.findCandidatesForFoundItem(event);
    }
}

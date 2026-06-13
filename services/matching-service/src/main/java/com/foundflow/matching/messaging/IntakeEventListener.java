package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.matching.service.CandidateMatchingService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

@Component
public class IntakeEventListener {

    private static final Logger log = LoggerFactory.getLogger(IntakeEventListener.class);
    private static final String SKIPPED_COUNTER = "matching.candidate_search.skipped";

    private final CandidateMatchingService candidateMatchingService;
    private final MeterRegistry meterRegistry;

    public IntakeEventListener(
            CandidateMatchingService candidateMatchingService,
            MeterRegistry meterRegistry
    ) {
        this.candidateMatchingService = candidateMatchingService;
        this.meterRegistry = meterRegistry;
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_LOST_REPORTS_QUEUE)
    public void onLostReportCreated(LostReportCreatedEvent event) {
        log.info(
                "Received LostReportCreated event {} for lostReport={} venue={}",
                event.eventId(),
                event.lostReportId(),
                event.venueId()
        );
        runCandidateSearch(
                "LostReportCreated",
                event.eventId(),
                () -> candidateMatchingService.findCandidatesForLostReport(event)
        );
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_LOST_REPORT_UPDATES_QUEUE)
    public void onLostReportUpdated(LostReportUpdatedEvent event) {
        log.info(
                "Received LostReportUpdated event {} for lostReport={} venue={}",
                event.eventId(),
                event.lostReportId(),
                event.venueId()
        );
        runCandidateSearch(
                "LostReportUpdated",
                event.eventId(),
                () -> candidateMatchingService.findCandidatesForLostReportUpdate(event)
        );
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_FOUND_ITEMS_QUEUE)
    public void onFoundItemLogged(FoundItemLoggedEvent event) {
        log.info(
                "Received FoundItemLogged event {} for foundItem={} venue={}",
                event.eventId(),
                event.foundItemId(),
                event.venueId()
        );
        runCandidateSearch(
                "FoundItemLogged",
                event.eventId(),
                () -> candidateMatchingService.findCandidatesForFoundItem(event)
        );
    }

    @RabbitListener(queues = FoundFlowEventRouting.MATCHING_FOUND_ITEM_UPDATES_QUEUE)
    public void onFoundItemUpdated(FoundItemUpdatedEvent event) {
        log.info(
                "Received FoundItemUpdated event {} for foundItem={} venue={}",
                event.eventId(),
                event.foundItemId(),
                event.venueId()
        );
        runCandidateSearch(
                "FoundItemUpdated",
                event.eventId(),
                () -> candidateMatchingService.findCandidatesForFoundItemUpdate(event)
        );
    }

    private void runCandidateSearch(String eventType, java.util.UUID eventId, Runnable action) {
        try {
            action.run();
        } catch (RestClientException exception) {
            log.warn(
                    "Candidate search failed for {} event {} after GenAI error; Rabbit retry/DLQ policy will apply: {}",
                    eventType,
                    eventId,
                    exception.getMessage()
            );
            Counter.builder(SKIPPED_COUNTER)
                    .tag("event_type", eventType)
                    .register(meterRegistry)
                    .increment();
            throw exception;
        }
    }
}

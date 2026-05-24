package com.foundflow.matching.service;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CandidateMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchingService.class);

    public void findCandidatesForLostReport(LostReportCreatedEvent event) {
        log.info(
                "Dummy candidate search triggered for lostReport={} venue={}",
                event.lostReportId(),
                event.venueId()
        );
    }

    public void findCandidatesForLostReportUpdate(LostReportUpdatedEvent event) {
        log.info(
                "Dummy candidate search triggered for updated lostReport={} venue={}",
                event.lostReportId(),
                event.venueId()
        );
    }

    public void findCandidatesForFoundItem(FoundItemLoggedEvent event) {
        log.info(
                "Dummy candidate search triggered for foundItem={} venue={}",
                event.foundItemId(),
                event.venueId()
        );
    }

    public void findCandidatesForFoundItemUpdate(FoundItemUpdatedEvent event) {
        log.info(
                "Dummy candidate search triggered for updated foundItem={} venue={}",
                event.foundItemId(),
                event.venueId()
        );
    }
}

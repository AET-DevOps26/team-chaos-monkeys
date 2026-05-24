package com.foundflow.matching.service;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.LostReportCreatedEvent;
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

    public void findCandidatesForFoundItem(FoundItemLoggedEvent event) {
        log.info(
                "Dummy candidate search triggered for foundItem={} venue={}",
                event.foundItemId(),
                event.venueId()
        );
    }
}

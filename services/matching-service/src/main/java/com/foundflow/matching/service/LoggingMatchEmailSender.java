package com.foundflow.matching.service;

import com.foundflow.matching.domain.MatchEmailLog;
import com.foundflow.matching.repository.MatchEmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LoggingMatchEmailSender implements MatchEmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingMatchEmailSender.class);

    private final MatchEmailLogRepository emailLogRepository;

    public LoggingMatchEmailSender(MatchEmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }

    @Override
    public void sendPublicMatchLink(String recipient, UUID venueId, UUID matchId, String matchUrl) {
        emailLogRepository.save(new MatchEmailLog(
                recipient,
                venueId,
                matchId,
                "FoundFlow may have found your item",
                "Use this link to view and confirm or reject the match: " + matchUrl,
                matchUrl,
                LocalDateTime.now()
        ));
        log.info("Public match email for {} with match link {}", recipient, matchUrl);
    }
}

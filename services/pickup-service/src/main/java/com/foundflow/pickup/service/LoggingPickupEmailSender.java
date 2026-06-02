package com.foundflow.pickup.service;

import com.foundflow.pickup.domain.PickupEmailLog;
import com.foundflow.pickup.repository.PickupEmailLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;

@Component
public class LoggingPickupEmailSender implements PickupEmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPickupEmailSender.class);

    private final PickupEmailLogRepository emailLogRepository;

    public LoggingPickupEmailSender(PickupEmailLogRepository emailLogRepository) {
        this.emailLogRepository = emailLogRepository;
    }

    @Override
    public void sendPickupScheduled(String recipient, UUID venueId, String manageUrl) {
        emailLogRepository.save(new PickupEmailLog(
                recipient,
                venueId,
                "Your FoundFlow pickup is scheduled",
                "Use this link to change or cancel your pickup: " + manageUrl,
                manageUrl,
                LocalDateTime.now()
        ));
        log.info("Pickup scheduled email for {} with manage link {}", recipient, manageUrl);
    }
}

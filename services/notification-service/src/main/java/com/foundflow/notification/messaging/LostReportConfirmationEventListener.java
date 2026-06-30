package com.foundflow.notification.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.notification.service.NotificationDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LostReportConfirmationEventListener {

    private static final Logger log = LoggerFactory.getLogger(LostReportConfirmationEventListener.class);

    private final NotificationDispatcher dispatcher;

    public LostReportConfirmationEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.NOTIFICATION_LOST_REPORT_CONFIRMATIONS_QUEUE)
    public void onLostReportCreated(LostReportCreatedEvent event) {
        if (event.contactEmail() == null || event.contactEmail().isBlank()) {
            log.debug("Lost report {} has no contact email; skipping confirmation.", event.lostReportId());
            return;
        }
        dispatcher.dispatchReportConfirmation(event.contactEmail(), event.venueId());
    }
}

package com.foundflow.matching.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportStatusChangeRequestedEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.UUID;

@Component
public class LostReportStatusEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public LostReportStatusEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishMatched(UUID lostReportId, UUID venueId) {
        publish(lostReportId, venueId, LostReportStatusChangeRequestedEvent.MATCHED);
    }

    public void publishReopened(UUID lostReportId, UUID venueId) {
        publish(lostReportId, venueId, LostReportStatusChangeRequestedEvent.OPEN);
    }

    private void publish(UUID lostReportId, UUID venueId, String status) {
        publishAfterCommit(new LostReportStatusChangeRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReportId,
                venueId,
                status
        ));
    }

    private void publishAfterCommit(LostReportStatusChangeRequestedEvent event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            send(event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(event);
            }
        });
    }

    private void send(LostReportStatusChangeRequestedEvent event) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.LOST_REPORT_STATUS_CHANGE_REQUESTED,
                event
        );
    }
}

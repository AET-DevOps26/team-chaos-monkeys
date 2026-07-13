package com.foundflow.lostitem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportDeletedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.lostitem.domain.LostReport;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Component
public class LostReportEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public LostReportEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishLostReportCreated(LostReport lostReport) {
        publishAfterCommit(FoundFlowEventRouting.LOST_REPORT_CREATED, toEvent(lostReport));
    }

    public void publishLostReportUpdated(LostReport lostReport) {
        publishAfterCommit(FoundFlowEventRouting.LOST_REPORT_UPDATED, toUpdatedEvent(lostReport));
    }

    public void publishLostReportDeleted(LostReport lostReport) {
        publishAfterCommit(
                FoundFlowEventRouting.LOST_REPORT_DELETED,
                new LostReportDeletedEvent(
                        UUID.randomUUID(),
                        Instant.now(),
                        lostReport.getId(),
                        lostReport.getVenueId()
                )
        );
    }

    private LostReportCreatedEvent toEvent(LostReport lostReport) {
        return new LostReportCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReport.getId(),
                lostReport.getVenueId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                toInstant(lostReport.getLostAt()),
                lostReport.getLocation(),
                lostReport.getStatus().name(),
                lostReport.getContactEmail(),
                toPayload(lostReport.getAttributes())
        );
    }

    private LostReportUpdatedEvent toUpdatedEvent(LostReport lostReport) {
        return new LostReportUpdatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReport.getId(),
                lostReport.getVenueId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                toInstant(lostReport.getLostAt()),
                lostReport.getLocation(),
                lostReport.getStatus().name(),
                lostReport.getContactEmail(),
                toPayload(lostReport.getAttributes())
        );
    }

    private void publishAfterCommit(String routingKey, Object event) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            send(routingKey, event);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                send(routingKey, event);
            }
        });
    }

    private void send(String routingKey, Object event) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                routingKey,
                event
        );
    }

    private ItemAttributesPayload toPayload(ItemAttributes attributes) {
        if (attributes == null) {
            return null;
        }

        return new ItemAttributesPayload(
                attributes.getCategory(),
                attributes.getDescription(),
                attributes.getBrand(),
                attributes.getColor(),
                attributes.getMarks()
        );
    }

    private Instant toInstant(LocalDateTime timestamp) {
        return timestamp == null ? null : timestamp.toInstant(ZoneOffset.UTC);
    }
}

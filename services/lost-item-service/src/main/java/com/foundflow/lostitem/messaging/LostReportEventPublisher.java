package com.foundflow.lostitem.messaging;

import com.foundflow.common.domain.ItemAttributes;
import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.lostitem.domain.LostReport;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class LostReportEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public LostReportEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publishLostReportCreated(LostReport lostReport) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.LOST_REPORT_CREATED,
                toEvent(lostReport)
        );
    }

    public void publishLostReportUpdated(LostReport lostReport) {
        rabbitTemplate.convertAndSend(
                FoundFlowEventRouting.EXCHANGE,
                FoundFlowEventRouting.LOST_REPORT_UPDATED,
                toUpdatedEvent(lostReport)
        );
    }

    private LostReportCreatedEvent toEvent(LostReport lostReport) {
        return new LostReportCreatedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                lostReport.getId(),
                lostReport.getVenueId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                lostReport.getLostAt(),
                lostReport.getLocation(),
                lostReport.getStatus().name(),
                toPayload(lostReport.getAttributes())
        );
    }

    private LostReportUpdatedEvent toUpdatedEvent(LostReport lostReport) {
        return new LostReportUpdatedEvent(
                UUID.randomUUID(),
                1,
                Instant.now(),
                lostReport.getId(),
                lostReport.getVenueId(),
                lostReport.getPhotoKey(),
                lostReport.getDescription(),
                lostReport.getLostAt(),
                lostReport.getLocation(),
                lostReport.getStatus().name(),
                toPayload(lostReport.getAttributes())
        );
    }

    private ItemAttributesPayload toPayload(ItemAttributes attributes) {
        if (attributes == null) {
            return null;
        }

        return new ItemAttributesPayload(
                attributes.getCategory(),
                attributes.getBrand(),
                attributes.getColor(),
                attributes.getMarks()
        );
    }
}

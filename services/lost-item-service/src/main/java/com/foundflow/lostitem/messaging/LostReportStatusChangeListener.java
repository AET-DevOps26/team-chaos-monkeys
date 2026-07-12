package com.foundflow.lostitem.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.LostReportStatusChangeRequestedEvent;
import com.foundflow.lostitem.service.LostReportService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class LostReportStatusChangeListener {

    private final LostReportService lostReportService;

    public LostReportStatusChangeListener(LostReportService lostReportService) {
        this.lostReportService = lostReportService;
    }

    @RabbitListener(queues = FoundFlowEventRouting.LOST_ITEM_STATUS_CHANGE_QUEUE)
    public void onStatusChangeRequested(LostReportStatusChangeRequestedEvent event) {
        lostReportService.applyMatchStatusChange(event.lostReportId(), event.status());
    }
}

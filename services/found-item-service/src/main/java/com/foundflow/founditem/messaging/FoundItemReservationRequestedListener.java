package com.foundflow.founditem.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.FoundItemReservationRequestedEvent;
import com.foundflow.founditem.service.FoundItemService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class FoundItemReservationRequestedListener {

    private final FoundItemService foundItemService;

    public FoundItemReservationRequestedListener(FoundItemService foundItemService) {
        this.foundItemService = foundItemService;
    }

    @RabbitListener(queues = FoundFlowEventRouting.FOUND_ITEM_RESERVATION_REQUESTS_QUEUE)
    public void onReservationRequested(FoundItemReservationRequestedEvent event) {
        foundItemService.reserveFoundItem(event.foundItemId());
    }
}

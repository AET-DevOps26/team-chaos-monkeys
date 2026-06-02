package com.foundflow.notification.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PickupConfirmationRequestedEvent;
import com.foundflow.notification.service.NotificationDispatcher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PickupConfirmationEventListener {

    private final NotificationDispatcher dispatcher;

    public PickupConfirmationEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.NOTIFICATION_PICKUP_CONFIRMATIONS_QUEUE)
    public void onPickupConfirmationRequested(PickupConfirmationRequestedEvent event) {
        dispatcher.dispatchPickupConfirmation(
                event.pickupId(),
                event.matchId(),
                event.recipient(),
                event.venueId()
        );
    }
}

package com.foundflow.notification.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.PasswordResetRequestedEvent;
import com.foundflow.notification.service.NotificationDispatcher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetEventListener {

    private final NotificationDispatcher dispatcher;

    public PasswordResetEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.NOTIFICATION_PASSWORD_RESETS_QUEUE)
    public void onPasswordResetRequested(PasswordResetRequestedEvent event) {
        dispatcher.dispatchPasswordReset(
                event.recipient(),
                event.venueId(),
                event.resetUrl()
        );
    }
}

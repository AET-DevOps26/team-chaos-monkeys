package com.foundflow.notification.messaging;

import com.foundflow.events.FoundFlowEventRouting;
import com.foundflow.events.MatchInviteRequestedEvent;
import com.foundflow.notification.service.NotificationDispatcher;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class MatchInviteEventListener {

    private final NotificationDispatcher dispatcher;

    public MatchInviteEventListener(NotificationDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    @RabbitListener(queues = FoundFlowEventRouting.NOTIFICATION_MATCH_INVITES_QUEUE)
    public void onMatchInviteRequested(MatchInviteRequestedEvent event) {
        dispatcher.dispatchMatchInvite(
                event.matchId(),
                event.recipient(),
                event.venueId(),
                event.matchUrl()
        );
    }
}

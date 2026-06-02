package com.foundflow.notification.messaging;

import com.foundflow.events.MatchInviteRequestedEvent;
import com.foundflow.events.PickupConfirmationRequestedEvent;
import com.foundflow.notification.service.NotificationDispatcher;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationListenerTest {

    @Mock
    private NotificationDispatcher dispatcher;

    @Test
    void matchInviteListener_delegatesEventFieldsToDispatcher() {
        MatchInviteEventListener listener = new MatchInviteEventListener(dispatcher);
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        listener.onMatchInviteRequested(new MatchInviteRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                matchId,
                "lost@example.com",
                venueId
        ));

        verify(dispatcher).dispatchMatchInvite(matchId, "lost@example.com", venueId);
    }

    @Test
    void pickupConfirmationListener_delegatesEventFieldsToDispatcher() {
        PickupConfirmationEventListener listener = new PickupConfirmationEventListener(dispatcher);
        UUID pickupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        listener.onPickupConfirmationRequested(new PickupConfirmationRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                pickupId,
                matchId,
                "lost@example.com",
                venueId
        ));

        verify(dispatcher).dispatchPickupConfirmation(pickupId, matchId, "lost@example.com", venueId);
    }
}

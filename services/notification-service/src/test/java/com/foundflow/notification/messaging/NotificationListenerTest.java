package com.foundflow.notification.messaging;

import com.foundflow.events.MatchInviteRequestedEvent;
import com.foundflow.events.PasswordResetRequestedEvent;
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
        String matchUrl = "http://localhost:3000/report/match/public-token";

        listener.onMatchInviteRequested(new MatchInviteRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                matchId,
                "lost@example.com",
                venueId,
                matchUrl
        ));

        verify(dispatcher).dispatchMatchInvite(matchId, "lost@example.com", venueId, matchUrl);
    }

    @Test
    void pickupConfirmationListener_delegatesEventFieldsToDispatcher() {
        PickupConfirmationEventListener listener = new PickupConfirmationEventListener(dispatcher);
        UUID pickupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String manageUrl = "http://localhost:8080/api/pickups/public/manage-token";

        listener.onPickupConfirmationRequested(new PickupConfirmationRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                pickupId,
                matchId,
                "lost@example.com",
                venueId,
                manageUrl
        ));

        verify(dispatcher).dispatchPickupConfirmation(matchId, "lost@example.com", venueId, manageUrl);
    }

    @Test
    void passwordResetListener_delegatesEventFieldsToDispatcher() {
        PasswordResetEventListener listener = new PasswordResetEventListener(dispatcher);
        UUID userId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String resetUrl = "http://localhost:8080/reset-password?token=reset-token";

        listener.onPasswordResetRequested(new PasswordResetRequestedEvent(
                UUID.randomUUID(),
                Instant.now(),
                userId,
                venueId,
                "staff@example.com",
                resetUrl
        ));

        verify(dispatcher).dispatchPasswordReset("staff@example.com", venueId, resetUrl);
    }
}

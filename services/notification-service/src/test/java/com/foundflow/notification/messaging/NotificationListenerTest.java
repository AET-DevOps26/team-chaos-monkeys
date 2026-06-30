package com.foundflow.notification.messaging;

import com.foundflow.events.LostReportCreatedEvent;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
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
        String matchUrl = "http://localhost:8080/api/matches/public/public-token";

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

    @Test
    void lostReportConfirmationListener_delegatesContactEmailAndVenueToDispatcher() {
        LostReportConfirmationEventListener listener = new LostReportConfirmationEventListener(dispatcher);
        UUID venueId = UUID.randomUUID();

        listener.onLostReportCreated(lostReportCreated(venueId, "lost@example.com"));

        verify(dispatcher).dispatchReportConfirmation("lost@example.com", venueId);
    }

    @Test
    void lostReportConfirmationListener_skipsWhenContactEmailMissing() {
        LostReportConfirmationEventListener listener = new LostReportConfirmationEventListener(dispatcher);

        listener.onLostReportCreated(lostReportCreated(UUID.randomUUID(), null));
        listener.onLostReportCreated(lostReportCreated(UUID.randomUUID(), "   "));

        verify(dispatcher, never()).dispatchReportConfirmation(anyString(), any());
    }

    private static LostReportCreatedEvent lostReportCreated(UUID venueId, String contactEmail) {
        return new LostReportCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                UUID.randomUUID(),
                venueId,
                null,
                "black leather wallet",
                Instant.now(),
                "Lobby",
                "OPEN",
                contactEmail,
                null
        );
    }
}

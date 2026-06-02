package com.foundflow.notification.service;

import com.foundflow.magiclink.MagicLinkService;
import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final String PUBLIC_BASE_URL = "http://localhost:8080";
    private static final String FROM_ADDRESS = "no-reply@foundflow.test";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private JavaMailSender mailSender;

    @Test
    void dispatchMatchInvite_persistsRowFirstThenSendsAndMarksSent() {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        when(magicLinkService.createMatchViewToken(matchId, venueId, recipient))
                .thenReturn("public-token");
        // The same Notification instance survives both save() calls. We snapshot
        // sentAt at each invocation because the entity is mutated in place between
        // them, so a post-hoc captor would only see the final state.
        java.util.List<java.time.LocalDateTime> sentAtAtEachSave = new java.util.ArrayList<>();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0);
                    sentAtAtEachSave.add(arg.getSentAt());
                    return arg;
                });

        dispatcher.dispatchMatchInvite(matchId, recipient, venueId);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(SimpleMailMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isEqualTo(matchId);
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("FoundFlow may have found your item");
        assertThat(finalState.getBody())
                .isEqualTo("Use this link to view and confirm or reject the match: "
                        + PUBLIC_BASE_URL + "/api/matches/public/public-token");
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0))
                .as("first save (URL persistence) must commit before the send attempt")
                .isNull();
        assertThat(sentAtAtEachSave.get(1))
                .as("second save records sentAt after a successful send")
                .isNotNull();

        ArgumentCaptor<SimpleMailMessage> mailCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        SimpleMailMessage sent = mailCaptor.getValue();
        assertThat(sent.getFrom()).isEqualTo(FROM_ADDRESS);
        assertThat(sent.getTo()).containsExactly(recipient);
        assertThat(sent.getSubject()).isEqualTo("FoundFlow may have found your item");
        assertThat(sent.getText()).isEqualTo(finalState.getBody());
    }

    @Test
    void dispatchMatchInvite_persistsRowEvenWhenSendThrows() {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        when(magicLinkService.createMatchViewToken(matchId, venueId, recipient))
                .thenReturn("public-token");
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> dispatcher.dispatchMatchInvite(matchId, recipient, venueId))
                .isInstanceOf(MailSendException.class);

        // The first save (URL persisted) happened before the throw; the second
        // save (sentAt) did not. JpaRepository.save() is called exactly once.
        verify(notificationRepository, times(1)).save(any(Notification.class));
    }

    @Test
    void dispatchPickupConfirmation_persistsRowFirstThenSendsAndMarksSent() {
        NotificationDispatcher dispatcher = dispatcher();
        UUID pickupId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        when(magicLinkService.createPickupManageToken(pickupId, matchId, venueId, recipient))
                .thenReturn("manage-token");
        java.util.List<java.time.LocalDateTime> sentAtAtEachSave = new java.util.ArrayList<>();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0);
                    sentAtAtEachSave.add(arg.getSentAt());
                    return arg;
                });

        dispatcher.dispatchPickupConfirmation(pickupId, matchId, recipient, venueId);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(SimpleMailMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isEqualTo(matchId);
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("Your FoundFlow pickup is scheduled");
        assertThat(finalState.getBody())
                .isEqualTo("Use this link to change or cancel your pickup: "
                        + PUBLIC_BASE_URL + "/api/pickups/public/manage-token");
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0)).isNull();
        assertThat(sentAtAtEachSave.get(1)).isNotNull();
    }

    @Test
    void dispatch_doesNotTouchMailWhenMagicLinkServiceThrows() {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        when(magicLinkService.createMatchViewToken(matchId, venueId, recipient))
                .thenThrow(new IllegalStateException("hmac failure"));

        assertThatThrownBy(() -> dispatcher.dispatchMatchInvite(matchId, recipient, venueId))
                .isInstanceOf(IllegalStateException.class);

        verify(notificationRepository, never()).save(any(Notification.class));
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    private NotificationDispatcher dispatcher() {
        return new NotificationDispatcher(
                notificationRepository,
                magicLinkService,
                mailSender,
                PUBLIC_BASE_URL,
                FROM_ADDRESS
        );
    }
}

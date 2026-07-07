package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.repository.NotificationRepository;
import jakarta.mail.BodyPart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import jakarta.mail.internet.MimeMultipart;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    private static final String FROM_ADDRESS = "no-reply@foundflow.test";
    private static final String MATCH_URL = "http://localhost:3000/report/match/public-token";
    private static final String MANAGE_URL = "http://localhost:8080/api/pickups/public/manage-token";
    private static final String RESET_URL = "http://localhost:8080/reset-password?token=reset-token";

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private JavaMailSender mailSender;

    @BeforeEach
    void stubMimeMessageCreation() {
        when(mailSender.createMimeMessage())
                .thenAnswer(invocation -> new MimeMessage(Session.getInstance(new Properties())));
    }

    @Test
    void dispatchMatchInvite_persistsRowFirstThenSendsAndMarksSent() throws Exception {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

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

        dispatcher.dispatchMatchInvite(matchId, recipient, venueId, MATCH_URL);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(MimeMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isEqualTo(matchId);
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("FoundFlow may have found your item");
        assertThat(finalState.getBody())
                .isEqualTo("Use this link to view and confirm or reject the match: " + MATCH_URL);
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0))
                .as("first save (URL persistence) must commit before the send attempt")
                .isNull();
        assertThat(sentAtAtEachSave.get(1))
                .as("second save records sentAt after a successful send")
                .isNotNull();

        MimeMessage sent = sentMessage();
        assertThat(sent.getFrom()[0].toString()).isEqualTo(FROM_ADDRESS);
        assertThat(sent.getAllRecipients()[0].toString()).isEqualTo(recipient);
        assertThat(sent.getSubject()).isEqualTo("FoundFlow may have found your item");
        assertThat(plainTextPart(sent))
                .as("plain-text fallback part carries the persisted body")
                .isEqualTo(finalState.getBody());
        String html = htmlPart(sent);
        assertThat(html).contains("FoundFlow");
        assertThat(html).contains("#7D33FF");
        assertThat(html).contains("href=\"" + MATCH_URL + "\"");
        assertThat(html).contains("View match");
        assertThat(html)
                .as("raw URL is printed below the CTA button")
                .containsSubsequence("</a>", MATCH_URL);
    }

    @Test
    void dispatchMatchInvite_htmlEscapesUntrustedUrl() throws Exception {
        NotificationDispatcher dispatcher = dispatcher();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        String hostileUrl = "http://localhost:3000/match?a=1&b=2\"><script>alert(1)</script>";
        dispatcher.dispatchMatchInvite(UUID.randomUUID(), "lost@example.com", UUID.randomUUID(), hostileUrl);

        String html = htmlPart(sentMessage());
        assertThat(html).doesNotContain("<script>");
        assertThat(html).doesNotContain("a=1&b=2\">");
        assertThat(html).contains("a=1&amp;b=2&quot;&gt;&lt;script&gt;");
    }

    @Test
    void dispatchMatchInvite_keepsPersistedRowWhenSendThrows() {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(MimeMessage.class));

        dispatcher.dispatchMatchInvite(matchId, recipient, venueId, MATCH_URL);

        // The URL stays visible via the persisted row; sentAt is only recorded
        // after a successful SMTP send.
        verify(notificationRepository, times(1)).save(any(Notification.class));
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void dispatchPickupConfirmation_persistsRowFirstThenSendsAndMarksSent() throws Exception {
        NotificationDispatcher dispatcher = dispatcher();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        java.util.List<java.time.LocalDateTime> sentAtAtEachSave = new java.util.ArrayList<>();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0);
                    sentAtAtEachSave.add(arg.getSentAt());
                    return arg;
                });

        dispatcher.dispatchPickupConfirmation(matchId, recipient, venueId, MANAGE_URL);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(MimeMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isEqualTo(matchId);
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("Your FoundFlow pickup is scheduled");
        assertThat(finalState.getBody())
                .isEqualTo("Use this link to change or cancel your pickup: " + MANAGE_URL);
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0)).isNull();
        assertThat(sentAtAtEachSave.get(1)).isNotNull();

        MimeMessage sent = sentMessage();
        assertThat(plainTextPart(sent)).isEqualTo(finalState.getBody());
        String html = htmlPart(sent);
        assertThat(html).contains("href=\"" + MANAGE_URL + "\"");
        assertThat(html).contains("Manage pickup");
    }

    @Test
    void dispatchPasswordReset_persistsRowFirstThenSendsAndMarksSent() throws Exception {
        NotificationDispatcher dispatcher = dispatcher();
        UUID venueId = UUID.randomUUID();
        String recipient = "staff@example.com";

        java.util.List<java.time.LocalDateTime> sentAtAtEachSave = new java.util.ArrayList<>();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0);
                    sentAtAtEachSave.add(arg.getSentAt());
                    return arg;
                });

        dispatcher.dispatchPasswordReset(recipient, venueId, RESET_URL);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(MimeMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isNull();
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("Reset your FoundFlow password");
        assertThat(finalState.getBody())
                .isEqualTo("Use this link to reset your FoundFlow password: " + RESET_URL);
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0)).isNull();
        assertThat(sentAtAtEachSave.get(1)).isNotNull();

        MimeMessage sent = sentMessage();
        assertThat(plainTextPart(sent)).isEqualTo(finalState.getBody());
        String html = htmlPart(sent);
        assertThat(html).contains("href=\"" + EmailHtml.escape(RESET_URL) + "\"");
        assertThat(html).contains("Reset password");
    }

    @Test
    void dispatchReportConfirmation_persistsRowFirstThenSendsAndMarksSent() throws Exception {
        NotificationDispatcher dispatcher = dispatcher();
        UUID venueId = UUID.randomUUID();
        String recipient = "lost@example.com";

        java.util.List<java.time.LocalDateTime> sentAtAtEachSave = new java.util.ArrayList<>();
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> {
                    Notification arg = invocation.getArgument(0);
                    sentAtAtEachSave.add(arg.getSentAt());
                    return arg;
                });

        dispatcher.dispatchReportConfirmation(recipient, venueId);

        ArgumentCaptor<Notification> savedCaptor = ArgumentCaptor.forClass(Notification.class);
        InOrder order = inOrder(notificationRepository, mailSender);
        order.verify(notificationRepository).save(savedCaptor.capture());
        order.verify(mailSender).send(any(MimeMessage.class));
        order.verify(notificationRepository).save(any(Notification.class));

        Notification finalState = savedCaptor.getValue();
        assertThat(finalState.getMatchId()).isNull();
        assertThat(finalState.getVenueId()).isEqualTo(venueId);
        assertThat(finalState.getRecipientAddress()).isEqualTo(recipient);
        assertThat(finalState.getSubject()).isEqualTo("We received your lost-item report");
        assertThat(finalState.getBody()).doesNotContain("http");
        assertThat(sentAtAtEachSave).hasSize(2);
        assertThat(sentAtAtEachSave.get(0)).isNull();
        assertThat(sentAtAtEachSave.get(1)).isNotNull();

        MimeMessage sent = sentMessage();
        assertThat(plainTextPart(sent)).isEqualTo(finalState.getBody());
        String html = htmlPart(sent);
        assertThat(html).contains("FoundFlow");
        assertThat(html)
                .as("receipt email carries no CTA link")
                .doesNotContain("href=");
    }

    private NotificationDispatcher dispatcher() {
        return new NotificationDispatcher(
                notificationRepository,
                mailSender,
                FROM_ADDRESS
        );
    }

    private MimeMessage sentMessage() throws Exception {
        ArgumentCaptor<MimeMessage> mailCaptor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(mailCaptor.capture());
        MimeMessage message = mailCaptor.getValue();
        // The real transport calls saveChanges() during send; the mock does not.
        // Without it the part Content-Type headers are unset and isMimeType lies.
        message.saveChanges();
        return message;
    }

    private static String plainTextPart(MimeMessage message) throws Exception {
        return findPart(message.getContent(), "text/plain");
    }

    private static String htmlPart(MimeMessage message) throws Exception {
        return findPart(message.getContent(), "text/html");
    }

    // MimeMessageHelper in multipart mode nests alternative inside related inside
    // mixed; walk the tree instead of hardcoding that structure.
    private static String findPart(Object content, String mimeType) throws Exception {
        if (content instanceof MimeMultipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                if (part.isMimeType(mimeType) && part.getContent() instanceof String text) {
                    return text;
                }
                String nested = findPart(part.getContent(), mimeType);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }
}

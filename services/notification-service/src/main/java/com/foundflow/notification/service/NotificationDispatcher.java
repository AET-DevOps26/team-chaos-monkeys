package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.repository.NotificationRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationDispatcher.class);

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DEFAULT_HEADER = "FoundFlow";

    private static final String MATCH_INVITE_SUBJECT = "FoundFlow may have found your item";
    private static final String MATCH_INVITE_BODY_PREFIX =
            "Use this link to view and confirm or reject the match: ";
    private static final String MATCH_INVITE_HTML_BODY =
            "Good news — an item logged at the venue may match your lost-item report. "
                    + "Review the match to confirm or reject it.";
    private static final String MATCH_INVITE_CTA_LABEL = "View match";

    private static final String PICKUP_CONFIRMATION_SUBJECT = "Your FoundFlow pickup is scheduled";
    private static final String PICKUP_CONFIRMATION_BODY_PREFIX =
            "Use this link to change or cancel your pickup: ";
    private static final String PICKUP_CONFIRMATION_HTML_BODY =
            "Your pickup is scheduled. You can change or cancel it at any time.";
    private static final String PICKUP_CONFIRMATION_CTA_LABEL = "Manage pickup";

    private static final String PASSWORD_RESET_SUBJECT = "Reset your FoundFlow password";
    private static final String PASSWORD_RESET_BODY_PREFIX =
            "Use this link to reset your FoundFlow password: ";
    private static final String PASSWORD_RESET_HTML_BODY =
            "We received a request to reset your FoundFlow password. "
                    + "If you did not request this, you can safely ignore this email.";
    private static final String PASSWORD_RESET_CTA_LABEL = "Reset password";

    private static final String REPORT_CONFIRMATION_SUBJECT = "We received your lost-item report";
    private static final String REPORT_CONFIRMATION_BODY =
            "Thanks for submitting your lost-item report. We have it on file and will email "
                    + "this address if we find a matching item.";

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final String fromAddress;

    public NotificationDispatcher(
            NotificationRepository notificationRepository,
            JavaMailSender mailSender,
            @Value("${foundflow.notifications.from-address}") String fromAddress
    ) {
        this.notificationRepository = notificationRepository;
        this.mailSender = mailSender;
        this.fromAddress = fromAddress;
    }

    // The owning service (matching / pickup) mints the magic-link token and ships
    // the rendered URL in the event so the link a user clicks is byte-identical to
    // the one returned by the API that triggered the notification.
    public void dispatchMatchInvite(UUID matchId, String recipient, UUID venueId, String matchUrl) {
        Notification notification = persist(
                matchId,
                venueId,
                recipient,
                MATCH_INVITE_SUBJECT,
                MATCH_INVITE_BODY_PREFIX + matchUrl
        );
        sendAndMarkSent(notification, EmailHtml.render(MATCH_INVITE_HTML_BODY, MATCH_INVITE_CTA_LABEL, matchUrl));
    }

    public void dispatchPickupConfirmation(UUID matchId, String recipient, UUID venueId, String manageUrl) {
        Notification notification = persist(
                matchId,
                venueId,
                recipient,
                PICKUP_CONFIRMATION_SUBJECT,
                PICKUP_CONFIRMATION_BODY_PREFIX + manageUrl
        );
        sendAndMarkSent(
                notification,
                EmailHtml.render(PICKUP_CONFIRMATION_HTML_BODY, PICKUP_CONFIRMATION_CTA_LABEL, manageUrl)
        );
    }

    // Unlike the link-bearing notifications, this is a plain receipt: no magic link,
    // and no match exists yet, so matchId is null (same as password reset).
    public void dispatchReportConfirmation(String recipient, UUID venueId) {
        Notification notification = persist(
                null,
                venueId,
                recipient,
                REPORT_CONFIRMATION_SUBJECT,
                REPORT_CONFIRMATION_BODY
        );
        sendAndMarkSent(notification, EmailHtml.render(REPORT_CONFIRMATION_BODY));
    }

    public void dispatchPasswordReset(String recipient, UUID venueId, String resetUrl) {
        Notification notification = persist(
                null,
                venueId,
                recipient,
                PASSWORD_RESET_SUBJECT,
                PASSWORD_RESET_BODY_PREFIX + resetUrl
        );
        sendAndMarkSent(notification, EmailHtml.render(PASSWORD_RESET_HTML_BODY, PASSWORD_RESET_CTA_LABEL, resetUrl));
    }

    private Notification persist(
            UUID matchId,
            UUID venueId,
            String recipient,
            String subject,
            String body
    ) {
        return notificationRepository.save(new Notification(
                matchId,
                venueId,
                recipient,
                DEFAULT_LANGUAGE,
                subject,
                DEFAULT_HEADER,
                body,
                null
        ));
    }

    private void sendAndMarkSent(Notification notification, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);
            helper.setFrom(fromAddress);
            helper.setTo(notification.getRecipientAddress());
            helper.setSubject(notification.getSubject());
            // Persisted plain-text body doubles as the text/plain fallback part.
            helper.setText(notification.getBody(), htmlBody);
            mailSender.send(message);
        } catch (MailException | MessagingException exception) {
            log.warn(
                    "Notification email send failed; persisted notification id={} recipient={}",
                    notification.getId(),
                    notification.getRecipientAddress(),
                    exception
            );
            return;
        }
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}

package com.foundflow.notification.service;

import com.foundflow.magiclink.MagicLinkService;
import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class NotificationDispatcher {

    private static final String DEFAULT_LANGUAGE = "en";
    private static final String DEFAULT_HEADER = "FoundFlow";

    private static final String MATCH_INVITE_SUBJECT = "FoundFlow may have found your item";
    private static final String MATCH_INVITE_BODY_PREFIX =
            "Use this link to view and confirm or reject the match: ";

    private static final String PICKUP_CONFIRMATION_SUBJECT = "Your FoundFlow pickup is scheduled";
    private static final String PICKUP_CONFIRMATION_BODY_PREFIX =
            "Use this link to change or cancel your pickup: ";

    private final NotificationRepository notificationRepository;
    private final MagicLinkService magicLinkService;
    private final JavaMailSender mailSender;
    private final String publicBaseUrl;
    private final String fromAddress;

    public NotificationDispatcher(
            NotificationRepository notificationRepository,
            MagicLinkService magicLinkService,
            JavaMailSender mailSender,
            @Value("${foundflow.public.base-url}") String publicBaseUrl,
            @Value("${foundflow.notifications.from-address}") String fromAddress
    ) {
        this.notificationRepository = notificationRepository;
        this.magicLinkService = magicLinkService;
        this.mailSender = mailSender;
        this.publicBaseUrl = publicBaseUrl;
        this.fromAddress = fromAddress;
    }

    public void dispatchMatchInvite(UUID matchId, String recipient, UUID venueId) {
        String token = magicLinkService.createMatchViewToken(matchId, venueId, recipient);
        String url = publicBaseUrl + "/api/matches/public/" + token;
        Notification notification = persist(
                matchId,
                venueId,
                recipient,
                MATCH_INVITE_SUBJECT,
                MATCH_INVITE_BODY_PREFIX + url
        );
        sendAndMarkSent(notification);
    }

    public void dispatchPickupConfirmation(UUID pickupId, UUID matchId, String recipient, UUID venueId) {
        String token = magicLinkService.createPickupManageToken(pickupId, matchId, venueId, recipient);
        String url = publicBaseUrl + "/api/pickups/public/" + token;
        Notification notification = persist(
                matchId,
                venueId,
                recipient,
                PICKUP_CONFIRMATION_SUBJECT,
                PICKUP_CONFIRMATION_BODY_PREFIX + url
        );
        sendAndMarkSent(notification);
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

    private void sendAndMarkSent(Notification notification) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(notification.getRecipientAddress());
        message.setSubject(notification.getSubject());
        message.setText(notification.getBody());
        mailSender.send(message);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    }
}

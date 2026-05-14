package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public NotificationResponse createNotification(CreateNotificationRequest request) {
        Notification notification = new Notification(
                request.matchId(),
                request.recipientAddress(),
                request.language(),
                request.subject(),
                request.header(),
                request.body(),
                null
        );

        Notification savedNotification = notificationRepository.save(notification);
        return toResponse(savedNotification);
    }

    public List<NotificationResponse> getAllNotifications() {
        return notificationRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<NotificationResponse> getNotificationById(UUID id) {
        return notificationRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<NotificationResponse> updateNotification(
            UUID id,
            UpdateNotificationRequest request
    ) {
        return notificationRepository.findById(id)
                .map(notification -> {
                    notification.setMatchId(request.matchId());
                    notification.setRecipientAddress(request.recipientAddress());
                    notification.setLanguage(request.language());
                    notification.setSubject(request.subject());
                    notification.setHeader(request.header());
                    notification.setBody(request.body());
                    notification.setSentAt(request.sentAt());

                    Notification updatedNotification = notificationRepository.save(notification);
                    return toResponse(updatedNotification);
                });
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMatchId(),
                notification.getRecipientAddress(),
                notification.getLanguage(),
                notification.getSubject(),
                notification.getHeader(),
                notification.getBody(),
                notification.getSentAt()
        );
    }
}
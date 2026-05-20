package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.repository.NotificationRepository;
import com.foundflow.notification.security.VenueAccessService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final VenueAccessService venueAccessService;

    public NotificationService(
            NotificationRepository notificationRepository,
            VenueAccessService venueAccessService
    ) {
        this.notificationRepository = notificationRepository;
        this.venueAccessService = venueAccessService;
    }

    public NotificationResponse createNotification(
            CreateNotificationRequest request,
            Jwt jwt
    ) {
        UUID venueId = venueAccessService.isAdmin(jwt)
                ? request.venueId()
                : venueAccessService.getVenueId(jwt);

        Notification notification = new Notification(
                request.matchId(),
                venueId,
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

    public List<NotificationResponse> getAllNotifications(
            String email,
            Jwt jwt
    ) {
        return findAccessibleNotifications(email, jwt)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<NotificationResponse> getNotificationById(
            UUID id,
            Jwt jwt
    ) {
        return notificationRepository.findById(id)
                .map(notification -> {
                    verifyVenueAccess(jwt, notification.getVenueId());
                    return notification;
                })
                .map(this::toResponse);
    }

    public Optional<NotificationResponse> updateNotification(
            UUID id,
            UpdateNotificationRequest request,
            Jwt jwt
    ) {
        return notificationRepository.findById(id)
                .map(notification -> {
                    verifyVenueAccess(jwt, notification.getVenueId());

                    notification.setMatchId(request.matchId());
                    if (venueAccessService.isAdmin(jwt)) {
                        notification.setVenueId(request.venueId());
                    }
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

    private List<Notification> findAccessibleNotifications(String email, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            if (email == null || email.isBlank()) {
                return notificationRepository.findAll();
            }

            return notificationRepository.findByRecipientAddress(email);
        }

        UUID venueId = venueAccessService.getVenueId(jwt);
        if (email == null || email.isBlank()) {
            return notificationRepository.findByVenueId(venueId);
        }

        return notificationRepository.findByVenueIdAndRecipientAddress(venueId, email);
    }

    private void verifyVenueAccess(Jwt jwt, UUID resourceVenueId) {
        if (!venueAccessService.canAccessVenue(jwt, resourceVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getMatchId(),
                notification.getVenueId(),
                notification.getRecipientAddress(),
                notification.getLanguage(),
                notification.getSubject(),
                notification.getHeader(),
                notification.getBody(),
                notification.getSentAt()
        );
    }
}

package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.repository.NotificationRepository;
import com.foundflow.notification.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createNotification_shouldUseVenueFromJwtForStaff() {
        NotificationService service = new NotificationService(notificationRepository, venueAccessService);

        UUID venueId = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        CreateNotificationRequest request = createRequest(matchId, UUID.randomUUID());

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = service.createNotification(request, staffJwt(venueId));

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        assertEquals(matchId, captor.getValue().getMatchId());
        assertEquals(venueId, captor.getValue().getVenueId());
        assertEquals(venueId, response.venueId());
        assertNull(response.sentAt());
    }

    @Test
    void getNotificationById_shouldReturnResponseForOwnVenue() {
        NotificationService service = new NotificationService(notificationRepository, venueAccessService);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Notification notification = notification(UUID.randomUUID(), venueId, "person@example.com", null);

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        Optional<NotificationResponse> response = service.getNotificationById(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(venueId, response.get().venueId());
        assertEquals("person@example.com", response.get().recipientAddress());
    }

    @Test
    void getAllNotifications_shouldFilterByVenueAndEmailForStaff() {
        NotificationService service = new NotificationService(notificationRepository, venueAccessService);

        UUID venueId = UUID.randomUUID();
        when(notificationRepository.findByVenueIdAndRecipientAddress(venueId, "person@example.com"))
                .thenReturn(List.of(notification(UUID.randomUUID(), venueId, "person@example.com", null)));

        List<NotificationResponse> responses =
                service.getAllNotifications("person@example.com", staffJwt(venueId));

        assertEquals(1, responses.size());
        assertEquals(venueId, responses.get(0).venueId());
        verify(notificationRepository)
                .findByVenueIdAndRecipientAddress(venueId, "person@example.com");
    }

    @Test
    void updateNotification_shouldKeepVenueForStaff() {
        NotificationService service = new NotificationService(notificationRepository, venueAccessService);

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Notification existing = notification(UUID.randomUUID(), venueId, "old@example.com", null);
        LocalDateTime sentAt = LocalDateTime.of(2026, 5, 13, 10, 15);
        UpdateNotificationRequest request = updateRequest(UUID.randomUUID(), UUID.randomUUID(), sentAt);

        when(notificationRepository.findById(id)).thenReturn(Optional.of(existing));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<NotificationResponse> response =
                service.updateNotification(id, request, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(venueId, response.get().venueId());
        assertEquals("updated@example.com", response.get().recipientAddress());
        assertEquals(sentAt, response.get().sentAt());
    }

    private CreateNotificationRequest createRequest(UUID matchId, UUID venueId) {
        return new CreateNotificationRequest(
                matchId,
                venueId,
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body"
        );
    }

    private UpdateNotificationRequest updateRequest(
            UUID matchId,
            UUID venueId,
            LocalDateTime sentAt
    ) {
        return new UpdateNotificationRequest(
                matchId,
                venueId,
                "updated@example.com",
                "en",
                "Updated subject",
                "Updated header",
                "Updated body",
                sentAt
        );
    }

    private Notification notification(
            UUID matchId,
            UUID venueId,
            String recipient,
            LocalDateTime sentAt
    ) {
        return new Notification(
                matchId,
                venueId,
                recipient,
                "de",
                "Betreff",
                "Header",
                "Body",
                sentAt
        );
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }
}

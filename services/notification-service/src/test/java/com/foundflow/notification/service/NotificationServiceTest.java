package com.foundflow.notification.service;

import com.foundflow.notification.domain.Notification;
import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

    @Test
    void createNotification_shouldSaveAndReturnNotification() {
        NotificationService notificationService = new NotificationService(notificationRepository);

        UUID matchId = UUID.randomUUID();

        CreateNotificationRequest request = new CreateNotificationRequest(
                matchId,
                "person@example.com",
                "de",
                "Wir haben möglicherweise deinen Gegenstand gefunden",
                "Gute Nachrichten!",
                "Es gibt einen möglichen Treffer zu deiner Verlustmeldung."
        );

        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationResponse response = notificationService.createNotification(request);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());

        Notification savedNotification = captor.getValue();

        assertEquals(matchId, savedNotification.getMatchId());
        assertEquals("person@example.com", savedNotification.getRecipientAddress());
        assertEquals("de", savedNotification.getLanguage());
        assertEquals("Wir haben möglicherweise deinen Gegenstand gefunden", savedNotification.getSubject());
        assertEquals("Gute Nachrichten!", savedNotification.getHeader());
        assertEquals("Es gibt einen möglichen Treffer zu deiner Verlustmeldung.", savedNotification.getBody());
        assertNull(savedNotification.getSentAt());

        assertEquals(matchId, response.matchId());
        assertEquals("person@example.com", response.recipientAddress());
        assertNull(response.sentAt());
    }

    @Test
    void getNotificationById_shouldReturnResponseWhenNotificationExists() {
        NotificationService notificationService = new NotificationService(notificationRepository);

        UUID id = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        Notification notification = new Notification(
                matchId,
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body",
                LocalDateTime.of(2026, 5, 12, 14, 30)
        );

        when(notificationRepository.findById(id)).thenReturn(Optional.of(notification));

        Optional<NotificationResponse> response = notificationService.getNotificationById(id);

        assertTrue(response.isPresent());
        assertEquals(matchId, response.get().matchId());
        assertEquals("person@example.com", response.get().recipientAddress());
        assertEquals("Betreff", response.get().subject());
        assertEquals(LocalDateTime.of(2026, 5, 12, 14, 30), response.get().sentAt());

        verify(notificationRepository).findById(id);
    }

    @Test
    void getNotificationById_shouldReturnEmptyWhenNotificationDoesNotExist() {
        NotificationService notificationService = new NotificationService(notificationRepository);

        UUID id = UUID.randomUUID();

        when(notificationRepository.findById(id)).thenReturn(Optional.empty());

        Optional<NotificationResponse> response = notificationService.getNotificationById(id);

        assertTrue(response.isEmpty());
        verify(notificationRepository).findById(id);
    }

    @Test
    void getAllNotifications_shouldReturnMappedResponses() {
        NotificationService notificationService = new NotificationService(notificationRepository);

        Notification notification1 = new Notification(
                UUID.randomUUID(),
                "first@example.com",
                "de",
                "Betreff A",
                "Header A",
                "Body A",
                null
        );

        Notification notification2 = new Notification(
                UUID.randomUUID(),
                "second@example.com",
                "en",
                "Subject B",
                "Header B",
                "Body B",
                LocalDateTime.of(2026, 5, 12, 15, 0)
        );

        when(notificationRepository.findAll()).thenReturn(List.of(notification1, notification2));

        List<NotificationResponse> responses = notificationService.getAllNotifications();

        assertEquals(2, responses.size());
        assertEquals("first@example.com", responses.get(0).recipientAddress());
        assertEquals("second@example.com", responses.get(1).recipientAddress());

        verify(notificationRepository).findAll();
    }

    @Test
    void updateNotification_shouldUpdateExistingNotification() {
        NotificationService notificationService = new NotificationService(notificationRepository);

        UUID id = UUID.randomUUID();

        Notification existingNotification = new Notification(
                UUID.randomUUID(),
                "old@example.com",
                "de",
                "Alter Betreff",
                "Alter Header",
                "Alter Body",
                null
        );

        UUID newMatchId = UUID.randomUUID();
        LocalDateTime sentAt = LocalDateTime.of(2026, 5, 13, 10, 15);

        UpdateNotificationRequest request = new UpdateNotificationRequest(
                newMatchId,
                "updated@example.com",
                "en",
                "Updated subject",
                "Updated header",
                "Updated body",
                sentAt
        );

        when(notificationRepository.findById(id)).thenReturn(Optional.of(existingNotification));
        when(notificationRepository.save(any(Notification.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<NotificationResponse> response =
                notificationService.updateNotification(id, request);

        assertTrue(response.isPresent());
        assertEquals(newMatchId, response.get().matchId());
        assertEquals("updated@example.com", response.get().recipientAddress());
        assertEquals("en", response.get().language());
        assertEquals("Updated subject", response.get().subject());
        assertEquals(sentAt, response.get().sentAt());

        verify(notificationRepository).findById(id);
        verify(notificationRepository).save(existingNotification);
    }
}
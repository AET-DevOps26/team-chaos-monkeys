package com.foundflow.notification.controller;

import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private NotificationService notificationService;

    @Test
    void createNotification_shouldReturnCreatedNotification() throws Exception {
        UUID id = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();

        CreateNotificationRequest request = new CreateNotificationRequest(
                matchId,
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body"
        );

        NotificationResponse response = new NotificationResponse(
                id,
                matchId,
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body",
                null
        );

        when(notificationService.createNotification(request)).thenReturn(response);

        mockMvc.perform(post("/api/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.recipientAddress").value("person@example.com"))
                .andExpect(jsonPath("$.language").value("de"))
                .andExpect(jsonPath("$.subject").value("Betreff"));
    }

    @Test
    void getAllNotifications_shouldReturnNotifications() throws Exception {
        NotificationResponse notification1 = new NotificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "first@example.com",
                "de",
                "Betreff A",
                "Header A",
                "Body A",
                null
        );

        NotificationResponse notification2 = new NotificationResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "second@example.com",
                "en",
                "Subject B",
                "Header B",
                "Body B",
                LocalDateTime.of(2026, 5, 12, 15, 0)
        );

        when(notificationService.getAllNotifications())
                .thenReturn(List.of(notification1, notification2));

        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientAddress").value("first@example.com"))
                .andExpect(jsonPath("$[1].recipientAddress").value("second@example.com"));
    }

    @Test
    void getNotificationById_shouldReturnNotificationWhenExists() throws Exception {
        UUID id = UUID.randomUUID();

        NotificationResponse response = new NotificationResponse(
                id,
                UUID.randomUUID(),
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body",
                null
        );

        when(notificationService.getNotificationById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/notifications/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.recipientAddress").value("person@example.com"))
                .andExpect(jsonPath("$.subject").value("Betreff"));
    }

    @Test
    void getNotificationById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();

        when(notificationService.getNotificationById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/notifications/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateNotification_shouldReturnUpdatedNotification() throws Exception {
        UUID id = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        LocalDateTime sentAt = LocalDateTime.of(2026, 5, 13, 10, 15);

        UpdateNotificationRequest request = new UpdateNotificationRequest(
                matchId,
                "updated@example.com",
                "en",
                "Updated subject",
                "Updated header",
                "Updated body",
                sentAt
        );

        NotificationResponse response = new NotificationResponse(
                id,
                matchId,
                "updated@example.com",
                "en",
                "Updated subject",
                "Updated header",
                "Updated body",
                sentAt
        );

        when(notificationService.updateNotification(id, request))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/notifications/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.recipientAddress").value("updated@example.com"))
                .andExpect(jsonPath("$.language").value("en"))
                .andExpect(jsonPath("$.subject").value("Updated subject"));
    }
}
package com.foundflow.notification.controller;

import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
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
        UUID venueId = UUID.randomUUID();
        CreateNotificationRequest request = createRequest(matchId, venueId);
        NotificationResponse response = response(id, matchId, venueId, null);

        when(notificationService.createNotification(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/notifications")
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/notifications/" + id))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void getAllNotifications_shouldSupportEmailFilter() throws Exception {
        UUID venueId = UUID.randomUUID();
        NotificationResponse response = response(
                UUID.randomUUID(),
                UUID.randomUUID(),
                venueId,
                null
        );

        when(notificationService.getAllNotifications(eq("person@example.com"), any(Jwt.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/notifications")
                        .param("email", "person@example.com")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].recipientAddress").value("person@example.com"));
    }

    @Test
    void getNotificationById_shouldReturnNotificationWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        NotificationResponse response = response(id, UUID.randomUUID(), venueId, null);

        when(notificationService.getNotificationById(eq(id), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/notifications/{id}", id)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateNotification_shouldReturnUpdatedNotification() throws Exception {
        UUID id = UUID.randomUUID();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LocalDateTime sentAt = LocalDateTime.of(2026, 5, 13, 10, 15);
        UpdateNotificationRequest request = updateRequest(matchId, venueId, sentAt);
        NotificationResponse response = response(id, matchId, venueId, sentAt);

        when(notificationService.updateNotification(eq(id), eq(request), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/notifications/{id}", id)
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentAt").exists());
    }

    @Test
    void blueprintReadEndpoints_shouldBeAvailableForStaff() throws Exception {
        UUID venueId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/bluePrints")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("default-lost-item-match"));

        UUID blueprintId = UUID.randomUUID();
        mockMvc.perform(get("/api/notifications/bluePrints/{id}", blueprintId)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(blueprintId.toString()));
    }

    @Test
    void blueprintWriteEndpoints_shouldAllowOpsManager() throws Exception {
        UUID venueId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/bluePrints")
                        .with(principal("OPS_MANAGER", venueId)))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/notifications/bluePrints/{id}", UUID.randomUUID())
                        .with(principal("OPS_MANAGER", venueId)))
                .andExpect(status().isAccepted());
    }

    @Test
    void blueprintWriteEndpoints_shouldRejectStaff() throws Exception {
        UUID venueId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/bluePrints")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/notifications/bluePrints/{id}", UUID.randomUUID())
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isForbidden());
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

    private NotificationResponse response(
            UUID id,
            UUID matchId,
            UUID venueId,
            LocalDateTime sentAt
    ) {
        return new NotificationResponse(
                id,
                matchId,
                venueId,
                "person@example.com",
                "de",
                "Betreff",
                "Header",
                "Body",
                sentAt
        );
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor staffPrincipal(UUID venueId) {
        return principal("STAFF", venueId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor principal(
            String role,
            UUID venueId
    ) {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of(role))
                .claim("venue_id", venueId.toString())
                .build();

        return request -> {
            request.setUserPrincipal(new JwtAuthenticationToken(token));
            return request;
        };
    }
}

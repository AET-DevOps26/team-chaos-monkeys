package com.foundflow.notification.config;

import com.foundflow.notification.controller.NotificationController;
import com.foundflow.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(NotificationController.class)
@Import(SecurityConfig.class)
class NotificationBlueprintSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void blueprintEndpoints_shouldRejectAnonymousRequests() throws Exception {
        UUID blueprintId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/bluePrints"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications/bluePrints/{id}", blueprintId))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/bluePrints"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/notifications/bluePrints/{id}", blueprintId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void blueprintReadEndpoints_shouldAllowAuthenticatedStaff() throws Exception {
        UUID blueprintId = UUID.randomUUID();

        mockMvc.perform(get("/api/notifications/bluePrints")
                        .with(role("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("default-lost-item-match"));

        mockMvc.perform(get("/api/notifications/bluePrints/{id}", blueprintId)
                        .with(role("STAFF")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(blueprintId.toString()));
    }

    @Test
    void blueprintWriteEndpoints_shouldRejectAuthenticatedStaff() throws Exception {
        UUID blueprintId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/bluePrints")
                        .with(role("STAFF")))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/notifications/bluePrints/{id}", blueprintId)
                        .with(role("STAFF")))
                .andExpect(status().isForbidden());
    }

    @Test
    void blueprintWriteEndpoints_shouldAllowOpsManager() throws Exception {
        UUID blueprintId = UUID.randomUUID();

        mockMvc.perform(post("/api/notifications/bluePrints")
                        .with(role("OPS_MANAGER")))
                .andExpect(status().isAccepted());

        mockMvc.perform(put("/api/notifications/bluePrints/{id}", blueprintId)
                        .with(role("OPS_MANAGER")))
                .andExpect(status().isAccepted());
    }

    private RequestPostProcessor role(String role) {
        return jwt()
                .jwt(token -> token
                        .claim("roles", List.of(role))
                        .claim("venue_id", UUID.randomUUID().toString()))
                .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }
}

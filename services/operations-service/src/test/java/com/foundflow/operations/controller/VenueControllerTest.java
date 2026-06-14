package com.foundflow.operations.controller;

import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.PublicVenueResponse;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueKpiResponse;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.service.VenueKpiService;
import com.foundflow.operations.service.VenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VenueController.class)
class VenueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private VenueService venueService;

    @MockitoBean
    private VenueKpiService venueKpiService;

    @Test
    void createVenue_shouldReturnCreatedVenue() throws Exception {
        UUID id = UUID.randomUUID();
        CreateVenueRequest request = new CreateVenueRequest("Chaos Arena", "friendly", "de");
        VenueResponse response = new VenueResponse(id, "Chaos Arena", "friendly", "de");

        when(venueService.createVenue(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/venues")
                        .with(adminPrincipal())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/venues/" + id))
                .andExpect(jsonPath("$.name").value("Chaos Arena"));
    }

    @Test
    void getAllVenues_shouldReturnVenues() throws Exception {
        UUID venueId = UUID.randomUUID();
        VenueResponse response = new VenueResponse(venueId, "Venue A", "formal", "de");

        when(venueService.getAllVenues(any(Jwt.class))).thenReturn(List.of(response));

        mockMvc.perform(get("/api/venues")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Venue A"));
    }

    @Test
    void getPublicVenues_shouldReturnVenueDirectoryWithoutAuthentication() throws Exception {
        UUID venueId = UUID.randomUUID();
        PublicVenueResponse response = new PublicVenueResponse(venueId, "Venue A");

        when(venueService.getPublicVenues()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/venues/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].venueId").value(venueId.toString()))
                .andExpect(jsonPath("$[0].name").value("Venue A"))
                .andExpect(jsonPath("$[0].tone").doesNotExist())
                .andExpect(jsonPath("$[0].defaultLanguage").doesNotExist());
    }

    @Test
    void getVenueById_shouldReturnVenueWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        VenueResponse response = new VenueResponse(id, "Chaos Arena", "friendly", "de");

        when(venueService.getVenueById(eq(id), any(Jwt.class))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/venues/{id}", id)
                        .with(staffPrincipal(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateVenue_shouldReturnUpdatedVenue() throws Exception {
        UUID id = UUID.randomUUID();
        UpdateVenueRequest request = new UpdateVenueRequest("Updated Venue", "professional", "en");
        VenueResponse response = new VenueResponse(id, "Updated Venue", "professional", "en");

        when(venueService.updateVenue(eq(id), eq(request), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/venues/{id}", id)
                        .with(opsManagerPrincipal(id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Venue"));
    }

    @Test
    void kpiEndpoints_shouldReturnAggregatedData() throws Exception {
        UUID venueId = UUID.randomUUID();
        when(venueService.getVenueById(eq(venueId), any(Jwt.class)))
                .thenReturn(Optional.of(new VenueResponse(venueId, "Venue A", "formal", "de")));
        when(venueKpiService.getKpis(any(Jwt.class)))
                .thenReturn(new VenueKpiResponse(venueId, 3, 4, 5, 2));
        when(venueKpiService.getKpis(eq(venueId), any(Jwt.class)))
                .thenReturn(new VenueKpiResponse(venueId, 3, 4, 5, 2));

        mockMvc.perform(get("/api/venues/kpis")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFoundItems").value(3))
                .andExpect(jsonPath("$.totalLostItems").value(4))
                .andExpect(jsonPath("$.totalMatches").value(5))
                .andExpect(jsonPath("$.pendingMatches").value(2));

        mockMvc.perform(get("/api/venues/kpis")
                        .param("venueId", venueId.toString())
                        .with(adminPrincipal()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void legacyKpiPath_shouldNotBeMapped() throws Exception {
        mockMvc.perform(get("/api/venues/kpis/{id}", UUID.randomUUID())
                        .with(adminPrincipal()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor staffPrincipal(UUID venueId) {
        return principal("STAFF", venueId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor opsManagerPrincipal(UUID venueId) {
        return principal("OPS_MANAGER", venueId);
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor principal(String role, UUID venueId) {
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor adminPrincipal() {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("ADMIN"))
                .build();

        return request -> {
            request.setUserPrincipal(new JwtAuthenticationToken(token));
            return request;
        };
    }
}

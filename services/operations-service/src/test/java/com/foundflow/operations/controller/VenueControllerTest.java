package com.foundflow.operations.controller;

import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.service.VenueService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Test
    void createVenue_shouldReturnCreatedVenue() throws Exception {
        UUID id = UUID.randomUUID();

        CreateVenueRequest request = new CreateVenueRequest(
                "Chaos Arena",
                "friendly",
                "de"
        );

        VenueResponse response = new VenueResponse(
                id,
                "Chaos Arena",
                "friendly",
                "de"
        );

        when(venueService.createVenue(request)).thenReturn(response);

        mockMvc.perform(post("/api/venues")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/venues/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Chaos Arena"))
                .andExpect(jsonPath("$.tone").value("friendly"))
                .andExpect(jsonPath("$.defaultLanguage").value("de"));
    }

    @Test
    void getAllVenues_shouldReturnVenues() throws Exception {
        VenueResponse venue1 = new VenueResponse(
                UUID.randomUUID(),
                "Venue A",
                "formal",
                "de"
        );

        VenueResponse venue2 = new VenueResponse(
                UUID.randomUUID(),
                "Venue B",
                "casual",
                "en"
        );

        when(venueService.getAllVenues()).thenReturn(List.of(venue1, venue2));

        mockMvc.perform(get("/api/venues"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Venue A"))
                .andExpect(jsonPath("$[1].name").value("Venue B"));
    }

    @Test
    void getVenueById_shouldReturnVenueWhenExists() throws Exception {
        UUID id = UUID.randomUUID();

        VenueResponse response = new VenueResponse(
                id,
                "Chaos Arena",
                "friendly",
                "de"
        );

        when(venueService.getVenueById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/venues/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Chaos Arena"));
    }

    @Test
    void getVenueById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();

        when(venueService.getVenueById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/venues/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateVenue_shouldReturnUpdatedVenue() throws Exception {
        UUID id = UUID.randomUUID();

        UpdateVenueRequest request = new UpdateVenueRequest(
                "Updated Venue",
                "professional",
                "en"
        );

        VenueResponse response = new VenueResponse(
                id,
                "Updated Venue",
                "professional",
                "en"
        );

        when(venueService.updateVenue(id, request)).thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/venues/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.name").value("Updated Venue"))
                .andExpect(jsonPath("$.tone").value("professional"))
                .andExpect(jsonPath("$.defaultLanguage").value("en"));
    }
}
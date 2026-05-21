package com.foundflow.founditem.controller;

import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.service.FoundItemService;
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

@WebMvcTest(FoundItemController.class)
class FoundItemControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private FoundItemService foundItemService;

    @Test
    void createFoundItem_shouldReturnCreatedItem() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        CreateFoundItemRequest request = createRequest(venueId, reporterId);
        FoundItemResponse response = response(id, venueId, reporterId, ItemStatus.STORED);

        when(foundItemService.createFoundItem(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/found-items")
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/found-items/" + id))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void getFoundItemById_shouldReturnItemWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        FoundItemResponse response = response(id, venueId, UUID.randomUUID(), ItemStatus.STORED);

        when(foundItemService.getFoundItemById(eq(id), any(Jwt.class))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/found-items/{id}", id)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()));
    }

    @Test
    void getFoundItemById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(foundItemService.getFoundItemById(eq(id), any(Jwt.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/found-items/{id}", id)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getAllFoundItems_shouldSupportStatusFilter() throws Exception {
        UUID venueId = UUID.randomUUID();
        FoundItemResponse response = response(UUID.randomUUID(), venueId, UUID.randomUUID(), ItemStatus.STORED);

        when(foundItemService.getAllFoundItems(eq(ItemStatus.STORED), any(Jwt.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/found-items")
                        .param("status", "STORED")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].venueId").value(venueId.toString()));
    }

    @Test
    void countAndHistogramEndpoints_shouldReturnKpiData() throws Exception {
        UUID venueId = UUID.randomUUID();

        when(foundItemService.countFoundItems(eq(ItemStatus.STORED), isNull(), any(Jwt.class)))
                .thenReturn(23L);
        when(foundItemService.getFoundItemHistogram(eq(ItemStatus.STORED), isNull(), any(Jwt.class)))
                .thenReturn(new com.foundflow.founditem.dto.HistogramResponse(
                        List.of(new com.foundflow.founditem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 19),
                                23
                        )),
                        List.of(new com.foundflow.founditem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 18),
                                23
                        )),
                        List.of(new com.foundflow.founditem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 1),
                                23
                        ))
                ));

        mockMvc.perform(get("/api/found-items/count")
                        .param("status", "STORED")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(23));

        mockMvc.perform(get("/api/found-items/histogram")
                        .param("status", "STORED")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDay[0].bucketStart").value("2026-05-19"))
                .andExpect(jsonPath("$.perDay[0].count").value(23));
    }

    @Test
    void updateFoundItem_shouldReturnUpdatedItem() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        UpdateFoundItemRequest request = updateRequest(venueId, reporterId);
        FoundItemResponse response = response(id, venueId, reporterId, ItemStatus.RESERVED);

        when(foundItemService.updateFoundItem(eq(id), eq(request), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/found-items/{id}", id)
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESERVED"));
    }

    private CreateFoundItemRequest createRequest(UUID venueId, UUID reporterId) {
        return new CreateFoundItemRequest(
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );
    }

    private UpdateFoundItemRequest updateRequest(UUID venueId, UUID reporterId) {
        return new UpdateFoundItemRequest(
                "photo-456",
                "Aktualisierte Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Info-Point",
                ItemStatus.RESERVED,
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Adidas", "Blue", List.of("Neues Merkmal"))
        );
    }

    private FoundItemResponse response(
            UUID id,
            UUID venueId,
            UUID reporterId,
            ItemStatus status
    ) {
        return new FoundItemResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                status,
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor staffPrincipal(UUID venueId) {
        Jwt token = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();

        return request -> {
            request.setUserPrincipal(new JwtAuthenticationToken(token));
            return request;
        };
    }
}

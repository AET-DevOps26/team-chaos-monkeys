package com.foundflow.founditem.controller;

import tools.jackson.databind.json.JsonMapper;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

        CreateFoundItemRequest request = new CreateFoundItemRequest(
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        FoundItemResponse response = new FoundItemResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(foundItemService.createFoundItem(request)).thenReturn(response);

        mockMvc.perform(post("/api/found-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/found-items/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Schwarzer Rucksack"))
                .andExpect(jsonPath("$.status").value("STORED"))
                .andExpect(jsonPath("$.attributes.brand").value("Nike"));
    }

    @Test
    void getFoundItemById_shouldReturnItemWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        FoundItemResponse response = new FoundItemResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(foundItemService.getFoundItemById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/found-items/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Schwarzer Rucksack"))
                .andExpect(jsonPath("$.status").value("STORED"));
    }

    @Test
    void getFoundItemById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();

        when(foundItemService.getFoundItemById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/found-items/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateFoundItem_shouldReturnUpdatedItem() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();

        UpdateFoundItemRequest request = new UpdateFoundItemRequest(
                "photo-456",
                "Aktualisierte Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Info-Point",
                ItemStatus.RESERVED,
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        FoundItemResponse response = new FoundItemResponse(
                id,
                "photo-456",
                "Aktualisierte Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Info-Point",
                ItemStatus.RESERVED,
                venueId,
                reporterId,
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        when(foundItemService.updateFoundItem(id, request)).thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/found-items/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Aktualisierte Beschreibung"))
                .andExpect(jsonPath("$.status").value("RESERVED"))
                .andExpect(jsonPath("$.attributes.color").value("Blue"));
    }
}
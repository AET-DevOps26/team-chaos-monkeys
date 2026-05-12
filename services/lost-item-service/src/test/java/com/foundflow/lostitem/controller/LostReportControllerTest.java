package com.foundflow.lostitem.controller;

import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.ItemAttributesDto;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.service.LostReportService;
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

@WebMvcTest(LostReportController.class)
class LostReportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private LostReportService lostReportService;

    @Test
    void createLostReport_shouldReturnCreatedReport() throws Exception {
        UUID id = UUID.randomUUID();

        CreateLostReportRequest request = new CreateLostReportRequest(
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                "person@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        LostReportResponse response = new LostReportResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ReportStatus.OPEN,
                "person@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(lostReportService.createLostReport(request)).thenReturn(response);

        mockMvc.perform(post("/api/lost-reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Schwarzer Rucksack verloren"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.attributes.brand").value("Nike"));
    }

    @Test
    void getLostReportById_shouldReturnReportWhenExists() throws Exception {
        UUID id = UUID.randomUUID();

        LostReportResponse response = new LostReportResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Bühne 2",
                ReportStatus.OPEN,
                "person@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Nike",
                        "Black",
                        List.of("Roter Anhänger")
                )
        );

        when(lostReportService.getLostReportById(id)).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/lost-reports/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Schwarzer Rucksack verloren"))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getLostReportById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();

        when(lostReportService.getLostReportById(id)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/lost-reports/{id}", id))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateLostReport_shouldReturnUpdatedReport() throws Exception {
        UUID id = UUID.randomUUID();

        UpdateLostReportRequest request = new UpdateLostReportRequest(
                "photo-456",
                "Aktualisierte Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Info-Point",
                ReportStatus.MATCHED,
                "updated@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        LostReportResponse response = new LostReportResponse(
                id,
                "photo-456",
                "Aktualisierte Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Info-Point",
                ReportStatus.MATCHED,
                "updated@example.com",
                new ItemAttributesDto(
                        "Bag",
                        "Adidas",
                        "Blue",
                        List.of("Neues Merkmal")
                )
        );

        when(lostReportService.updateLostReport(id, request)).thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/lost-reports/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.description").value("Aktualisierte Beschreibung"))
                .andExpect(jsonPath("$.status").value("MATCHED"))
                .andExpect(jsonPath("$.attributes.color").value("Blue"));
    }
}
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
        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(venueId);
        LostReportResponse response = response(id, venueId, ReportStatus.OPEN);

        when(lostReportService.createLostReport(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/lost-items")
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/lost-items/" + id))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void createLostReport_shouldAllowPublicRequest() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        CreateLostReportRequest request = createRequest(venueId);
        LostReportResponse response = response(id, venueId, ReportStatus.OPEN);

        when(lostReportService.createLostReport(eq(request), isNull())).thenReturn(response);

        mockMvc.perform(post("/api/lost-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/lost-items/" + id))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void getAllLostReports_shouldSupportStatusFilter() throws Exception {
        UUID venueId = UUID.randomUUID();
        LostReportResponse response = response(UUID.randomUUID(), venueId, ReportStatus.OPEN);

        when(lostReportService.getAllLostReports(eq(ReportStatus.OPEN), any(Jwt.class)))
                .thenReturn(List.of(response));

        mockMvc.perform(get("/api/lost-items")
                        .param("status", "OPEN")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].venueId").value(venueId.toString()));
    }

    @Test
    void countAndHistogramEndpoints_shouldReturnKpiData() throws Exception {
        UUID venueId = UUID.randomUUID();

        when(lostReportService.countLostReports(eq(ReportStatus.OPEN), isNull(), any(Jwt.class)))
                .thenReturn(12L);
        when(lostReportService.getLostReportHistogram(eq(ReportStatus.OPEN), isNull(), any(Jwt.class)))
                .thenReturn(new com.foundflow.lostitem.dto.HistogramResponse(
                        List.of(new com.foundflow.lostitem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 19),
                                12
                        )),
                        List.of(new com.foundflow.lostitem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 18),
                                12
                        )),
                        List.of(new com.foundflow.lostitem.dto.TimeBucketCount(
                                java.time.LocalDate.of(2026, 5, 1),
                                12
                        ))
                ));

        mockMvc.perform(get("/api/lost-items/count")
                        .param("status", "OPEN")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(12));

        mockMvc.perform(get("/api/lost-items/histogram")
                        .param("status", "OPEN")
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDay[0].bucketStart").value("2026-05-19"))
                .andExpect(jsonPath("$.perDay[0].count").value(12));
    }

    @Test
    void getLostReportById_shouldReturnReportWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        LostReportResponse response = response(id, venueId, ReportStatus.OPEN);

        when(lostReportService.getLostReportById(eq(id), any(Jwt.class))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/lost-items/{id}", id)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void updateLostReport_shouldReturnUpdatedReport() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UpdateLostReportRequest request = updateRequest(venueId);
        LostReportResponse response = response(id, venueId, ReportStatus.MATCHED);

        when(lostReportService.updateLostReport(eq(id), eq(request), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/lost-items/{id}", id)
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATCHED"));
    }

    private CreateLostReportRequest createRequest(UUID venueId) {
        return new CreateLostReportRequest(
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                venueId,
                "person@example.com",
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );
    }

    private UpdateLostReportRequest updateRequest(UUID venueId) {
        return new UpdateLostReportRequest(
                "photo-456",
                "Neue Beschreibung",
                LocalDateTime.of(2026, 5, 13, 9, 15),
                "Neuer Ort",
                ReportStatus.MATCHED,
                venueId,
                "new@example.com",
                new ItemAttributesDto("Bag", "Adidas", "Blue", List.of("Neues Merkmal"))
        );
    }

    private LostReportResponse response(UUID id, UUID venueId, ReportStatus status) {
        return new LostReportResponse(
                id,
                "photo-123",
                "Schwarzer Rucksack verloren",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                status,
                venueId,
                "person@example.com",
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

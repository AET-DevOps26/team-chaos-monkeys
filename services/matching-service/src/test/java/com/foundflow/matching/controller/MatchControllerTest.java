package com.foundflow.matching.controller;

import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.service.MatchService;
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

@WebMvcTest(MatchController.class)
class MatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JsonMapper jsonMapper;

    @MockitoBean
    private MatchService matchService;

    @Test
    void createMatch_shouldReturnCreatedMatch() throws Exception {
        UUID id = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        CreateMatchRequest request = new CreateMatchRequest(
                foundItemId,
                lostReportId,
                venueId,
                0.75f,
                0.90f,
                0.84f
        );

        MatchResponse response = new MatchResponse(
                id,
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                0.75f,
                0.90f,
                0.84f,
                LocalDateTime.of(2026, 5, 12, 14, 30)
        );

        when(matchService.createMatch(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/matches")
                        .with(staffJwt(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/matches/" + id))
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.foundItemId").value(foundItemId.toString()))
                .andExpect(jsonPath("$.lostReportId").value(lostReportId.toString()))
                .andExpect(jsonPath("$.venueId").value(venueId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.combinedScore").value(0.84));
    }

    @Test
    void getAllMatches_shouldReturnMatches() throws Exception {
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        MatchResponse match1 = new MatchResponse(
                UUID.randomUUID(),
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                0.60f,
                0.80f,
                0.72f,
                LocalDateTime.of(2026, 5, 12, 10, 0)
        );

        MatchResponse match2 = new MatchResponse(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                venueId,
                MatchStatus.CONFIRMED,
                0.90f,
                0.95f,
                0.93f,
                LocalDateTime.of(2026, 5, 12, 11, 0)
        );

        when(matchService.getAllMatches(
                eq(foundItemId),
                eq(lostReportId),
                eq(MatchStatus.PENDING),
                any(Jwt.class)
        ))
                .thenReturn(List.of(match1, match2));

        mockMvc.perform(get("/api/matches")
                        .param("foundItem", foundItemId.toString())
                        .param("lostItem", lostReportId.toString())
                        .param("status", "PENDING")
                        .with(staffJwt(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].combinedScore").value(0.72))
                .andExpect(jsonPath("$[1].combinedScore").value(0.93));
    }

    @Test
    void countAndHistogramEndpoints_shouldSupportCombinedFilters() throws Exception {
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        when(matchService.countMatches(
                eq(foundItemId),
                eq(lostReportId),
                eq(MatchStatus.PENDING),
                isNull(),
                any(Jwt.class)
        )).thenReturn(5L);
        when(matchService.getMatchHistogram(
                eq(foundItemId),
                eq(lostReportId),
                eq(MatchStatus.PENDING),
                isNull(),
                any(Jwt.class)
        )).thenReturn(new com.foundflow.matching.dto.HistogramResponse(
                List.of(new com.foundflow.matching.dto.TimeBucketCount(
                        java.time.LocalDate.of(2026, 5, 19),
                        5
                )),
                List.of(new com.foundflow.matching.dto.TimeBucketCount(
                        java.time.LocalDate.of(2026, 5, 18),
                        5
                )),
                List.of(new com.foundflow.matching.dto.TimeBucketCount(
                        java.time.LocalDate.of(2026, 5, 1),
                        5
                ))
        ));

        mockMvc.perform(get("/api/matches/count")
                        .param("foundItem", foundItemId.toString())
                        .param("lostItem", lostReportId.toString())
                        .param("status", "PENDING")
                        .with(staffJwt(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));

        mockMvc.perform(get("/api/matches/histogram")
                        .param("foundItem", foundItemId.toString())
                        .param("lostItem", lostReportId.toString())
                        .param("status", "PENDING")
                        .with(staffJwt(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.perDay[0].bucketStart").value("2026-05-19"))
                .andExpect(jsonPath("$.perDay[0].count").value(5));
    }

    @Test
    void getMatchById_shouldReturnMatchWhenExists() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        MatchResponse response = new MatchResponse(
                id,
                UUID.randomUUID(),
                UUID.randomUUID(),
                venueId,
                MatchStatus.PENDING,
                0.70f,
                0.85f,
                0.79f,
                LocalDateTime.of(2026, 5, 12, 12, 0)
        );

        when(matchService.getMatchById(eq(id), any(Jwt.class))).thenReturn(Optional.of(response));

        mockMvc.perform(get("/api/matches/{id}", id)
                        .with(staffJwt(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.combinedScore").value(0.79));
    }

    @Test
    void getMatchById_shouldReturnNotFoundWhenMissing() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(matchService.getMatchById(eq(id), any(Jwt.class))).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/matches/{id}", id)
                        .with(staffJwt(venueId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateMatch_shouldReturnUpdatedMatch() throws Exception {
        UUID id = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        UpdateMatchRequest request = new UpdateMatchRequest(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.CONFIRMED,
                0.88f,
                0.91f,
                0.90f
        );

        MatchResponse response = new MatchResponse(
                id,
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.CONFIRMED,
                0.88f,
                0.91f,
                0.90f,
                LocalDateTime.of(2026, 5, 12, 15, 0)
        );

        when(matchService.updateMatch(eq(id), eq(request), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(put("/api/matches/{id}", id)
                        .with(staffJwt(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.attributeScore").value(0.88))
                .andExpect(jsonPath("$.semanticScore").value(0.91))
                .andExpect(jsonPath("$.combinedScore").value(0.90));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor staffJwt(UUID venueId) {
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

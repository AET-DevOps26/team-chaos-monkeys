package com.foundflow.matching.controller;

import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.dto.ItemSearchResponse;
import com.foundflow.matching.dto.SearchResultItem;
import com.foundflow.matching.service.ItemSearchService;
import com.foundflow.matching.service.SearchUnavailableException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ItemSearchController.class)
class ItemSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ItemSearchService itemSearchService;

    @Test
    void search_returnsGroundedAnswerWithCitationsAndResults() throws Exception {
        UUID venueId = UUID.randomUUID();
        String itemId = UUID.randomUUID().toString();

        ItemSearchResponse response = new ItemSearchResponse(
                "The closest match is a black leather wallet found near the lobby [1].",
                true,
                List.of(itemId),
                List.of(new SearchResultItem(itemId, ItemType.FOUND, "Wallet", "black leather wallet near lobby", 0.12f))
        );
        when(itemSearchService.search(any(), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/matches/search")
                        .with(staffJwt(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"black leather wallet near lobby\",\"scope\":\"both\",\"k\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("The closest match is a black leather wallet found near the lobby [1]."))
                .andExpect(jsonPath("$.grounded").value(true))
                .andExpect(jsonPath("$.citations[0]").value(itemId))
                .andExpect(jsonPath("$.results[0].id").value(itemId))
                .andExpect(jsonPath("$.results[0].itemType").value("FOUND"))
                .andExpect(jsonPath("$.results[0].category").value("Wallet"))
                .andExpect(jsonPath("$.results[0].distance").value(0.12));
    }

    @Test
    void search_returnsDegradedResultsWithNullAnswerWhenUngrounded() throws Exception {
        UUID venueId = UUID.randomUUID();
        String itemId = UUID.randomUUID().toString();

        ItemSearchResponse response = new ItemSearchResponse(
                null,
                false,
                List.of(),
                List.of(new SearchResultItem(itemId, ItemType.LOST, "Bag", "blue backpack", 0.4f))
        );
        when(itemSearchService.search(any(), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/matches/search")
                        .with(staffJwt(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"blue backpack\",\"scope\":\"lost\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").doesNotExist())
                .andExpect(jsonPath("$.grounded").value(false))
                .andExpect(jsonPath("$.citations").isEmpty())
                .andExpect(jsonPath("$.results[0].id").value(itemId))
                .andExpect(jsonPath("$.results[0].itemType").value("LOST"));
    }

    @Test
    void search_rejectsBlankQueryWith400() throws Exception {
        mockMvc.perform(post("/api/matches/search")
                        .with(staffJwt(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"   \",\"scope\":\"both\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void search_rejectsMissingQueryWith400() throws Exception {
        mockMvc.perform(post("/api/matches/search")
                        .with(staffJwt(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"scope\":\"both\"}"))
                .andExpect(status().isBadRequest());
    }

    // Note: venue-scope 403 is enforced by SecurityConfig (/api/** hasAnyRole) + VenueAccessService
    // (throws AccessDeniedException on a missing venue_id claim, → 403 via Spring Security's
    // ExceptionTranslationFilter in the full context). @WebMvcTest does not load SecurityConfig, so
    // that translation can't be exercised here; the service-level propagation is covered in
    // ItemSearchServiceTest#search_missingVenueClaim_propagatesAccessDenied.

    @Test
    void search_returns503WhenEmbedUnavailable() throws Exception {
        when(itemSearchService.search(any(), any(Jwt.class)))
                .thenThrow(new SearchUnavailableException("Query embedding is unavailable."));

        mockMvc.perform(post("/api/matches/search")
                        .with(staffJwt(UUID.randomUUID()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"anything\",\"scope\":\"both\"}"))
                .andExpect(status().isServiceUnavailable());
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

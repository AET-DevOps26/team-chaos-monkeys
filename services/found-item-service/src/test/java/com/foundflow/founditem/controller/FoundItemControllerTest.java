package com.foundflow.founditem.controller;

import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.ItemAttributesDto;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.service.FoundItemService;
import com.foundflow.photo.storage.PhotoUrlResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
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
    void createFoundItem_shouldReturnCreatedItemWithoutPhotoKey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        CreateFoundItemRequest request = createRequest(venueId, reporterId);
        FoundItemResponse response = new FoundItemResponse(
                id,
                null,
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );

        when(foundItemService.createFoundItem(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/found-items")
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/found-items/" + id))
                .andExpect(jsonPath("$.photoKey").doesNotExist());
    }

    @Test
    void createFoundItem_shouldAllowMissingReporterId() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        CreateFoundItemRequest request = createRequest(venueId, null);
        FoundItemResponse response = response(id, venueId, reporterId, ItemStatus.STORED);

        when(foundItemService.createFoundItem(eq(request), any(Jwt.class))).thenReturn(response);

        mockMvc.perform(post("/api/found-items")
                        .with(staffPrincipal(venueId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reporterId").value(reporterId.toString()));
    }

    @Test
    void createFoundItemMultipart_shouldReturnUnsupportedMediaType() throws Exception {
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        CreateFoundItemRequest request = createRequest(venueId, reporterId);
        MockMultipartFile requestPart = new MockMultipartFile(
                "request",
                "request.json",
                MediaType.APPLICATION_JSON_VALUE,
                jsonMapper.writeValueAsBytes(request)
        );

        mockMvc.perform(multipart("/api/found-items")
                        .file(requestPart)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isUnsupportedMediaType());
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
        UUID reporterId = UUID.randomUUID();

        when(foundItemService.countFoundItems(eq(ItemStatus.STORED), isNull(), any(Jwt.class)))
                .thenReturn(23L);
        when(foundItemService.getFoundItemHistogram(
                eq(ItemStatus.STORED),
                eq(venueId),
                eq(reporterId),
                any(Jwt.class)
        ))
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
                        .param("venueId", venueId.toString())
                        .param("reporterId", reporterId.toString())
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

    @Test
    void updateFoundItemPhoto_shouldReturnItemWithGeneratedPhotoKey() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        MockMultipartFile photo = new MockMultipartFile(
                "photo",
                "bag.jpg",
                MediaType.IMAGE_JPEG_VALUE,
                "photo-bytes".getBytes()
        );
        FoundItemResponse response = new FoundItemResponse(
                id,
                "found-items/2026/05/generated.jpg",
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                ItemStatus.STORED,
                venueId,
                reporterId,
                new ItemAttributesDto("Bag", "Nike", "Black", List.of("Roter Anhaenger"))
        );

        when(foundItemService.updateFoundItemPhoto(eq(id), any(), any(Jwt.class)))
                .thenReturn(Optional.of(response));

        mockMvc.perform(multipart("/api/found-items/{id}/photo", id)
                        .file(photo)
                        .with(request -> {
                            request.setMethod("PUT");
                            return request;
                        })
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.photoKey").value("found-items/2026/05/generated.jpg"));
    }

    @Test
    void getFoundItemPhotoUrl_shouldReturnSignedUrl() throws Exception {
        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        URI signedUrl = URI.create("http://localhost:9000/foundflow-found-photos/photo-123?signature=test");

        when(foundItemService.getFoundItemPhotoUrl(eq(id), any(Jwt.class)))
                .thenReturn(Optional.of(new PhotoUrlResponse(signedUrl)));

        mockMvc.perform(get("/api/found-items/{id}/photo-url", id)
                        .with(staffPrincipal(venueId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value(signedUrl.toString()));
    }

    private CreateFoundItemRequest createRequest(UUID venueId, UUID reporterId) {
        return new CreateFoundItemRequest(
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

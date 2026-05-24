package com.foundflow.founditem.controller;

import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.CountResponse;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.HistogramResponse;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.domain.ItemStatus;
import com.foundflow.founditem.service.FoundItemService;
import com.foundflow.photo.storage.PhotoData;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.net.URI;

@RestController
@RequestMapping("/api/found-items")
public class FoundItemController {

    private final FoundItemService foundItemService;

    public FoundItemController(FoundItemService foundItemService) {
        this.foundItemService = foundItemService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<FoundItemResponse> createFoundItem(
            @Valid @RequestBody CreateFoundItemRequest request,
            JwtAuthenticationToken authentication
    ) {
        FoundItemResponse response = foundItemService.createFoundItem(request, authentication.getToken());
        return ResponseEntity
                .created(URI.create("/api/found-items/" + response.id()))
                .body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoundItemResponse> createFoundItemWithPhoto(
            @Valid @RequestPart("request") CreateFoundItemRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            JwtAuthenticationToken authentication
    ) {
        FoundItemResponse response = foundItemService.createFoundItem(request, photo, authentication.getToken());
        return ResponseEntity
                .created(URI.create("/api/found-items/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FoundItemResponse>> getAllFoundItems(
            @RequestParam(required = false) ItemStatus status,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(foundItemService.getAllFoundItems(status, authentication.getToken()));
    }

    @GetMapping("/count")
    public ResponseEntity<CountResponse> countFoundItems(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(new CountResponse(
                foundItemService.countFoundItems(status, venueId, authentication.getToken())
        ));
    }

    @GetMapping("/histogram")
    public ResponseEntity<HistogramResponse> getFoundItemHistogram(
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(foundItemService.getFoundItemHistogram(
                status,
                venueId,
                authentication.getToken()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoundItemResponse> getFoundItemById(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return foundItemService.getFoundItemById(id, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<FoundItemResponse> updateFoundItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFoundItemRequest request,
            JwtAuthenticationToken authentication
    ) {
        return foundItemService.updateFoundItem(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FoundItemResponse> updateFoundItemPhoto(
            @PathVariable UUID id,
            @RequestPart("photo") MultipartFile photo,
            JwtAuthenticationToken authentication
    ) {
        return foundItemService.updateFoundItemPhoto(id, photo, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<InputStreamResource> getFoundItemPhoto(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return foundItemService.getFoundItemPhoto(id, authentication.getToken())
                .map(this::photoResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFoundItem(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        if (foundItemService.deleteFoundItem(id, authentication.getToken())) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }

    private ResponseEntity<InputStreamResource> photoResponse(PhotoData photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.sizeBytes())
                .body(new InputStreamResource(photo.content()));
    }
}

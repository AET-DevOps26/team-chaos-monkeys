package com.foundflow.lostitem.controller;

import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.CountResponse;
import com.foundflow.lostitem.dto.HistogramResponse;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.service.LostReportService;
import com.foundflow.photo.storage.PhotoData;
import com.foundflow.photo.storage.PhotoUrlResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/lost-items", "/api/lost-reports"})
public class LostReportController {

    private final LostReportService lostReportService;

    public LostReportController(LostReportService lostReportService) {
        this.lostReportService = lostReportService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<LostReportResponse> createLostReport(
            @Valid @RequestBody CreateLostReportRequest request,
            JwtAuthenticationToken authentication
    ) {
        LostReportResponse response = lostReportService.createLostReport(
                request,
                authentication == null ? null : authentication.getToken()
        );
        return ResponseEntity
        .created(URI.create("/api/lost-items/" + response.id()))
        .body(response);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LostReportResponse> createLostReportWithPhoto(
            @Valid @RequestPart("request") CreateLostReportRequest request,
            @RequestPart(value = "photo", required = false) MultipartFile photo,
            JwtAuthenticationToken authentication
    ) {
        LostReportResponse response = lostReportService.createLostReport(
                request,
                photo,
                authentication == null ? null : authentication.getToken()
        );
        return ResponseEntity
                .created(URI.create("/api/lost-items/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<LostReportResponse>> getAllLostReports(
            @RequestParam(required = false) ReportStatus status,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(lostReportService.getAllLostReports(status, authentication.getToken()));
    }

    @GetMapping("/count")
    public ResponseEntity<CountResponse> countLostReports(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(new CountResponse(
                lostReportService.countLostReports(status, venueId, authentication.getToken())
        ));
    }

    @GetMapping("/histogram")
    public ResponseEntity<HistogramResponse> getLostReportHistogram(
            @RequestParam(required = false) ReportStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(lostReportService.getLostReportHistogram(
                status,
                venueId,
                authentication.getToken()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LostReportResponse> getLostReportById(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return lostReportService.getLostReportById(id, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LostReportResponse> updateLostReport(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLostReportRequest request,
            JwtAuthenticationToken authentication
    ) {
        return lostReportService.updateLostReport(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping(path = "/{id}/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<LostReportResponse> updateLostReportPhoto(
            @PathVariable UUID id,
            @RequestPart("photo") MultipartFile photo,
            JwtAuthenticationToken authentication
    ) {
        return lostReportService.updateLostReportPhoto(id, photo, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/photo")
    public ResponseEntity<InputStreamResource> getLostReportPhoto(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return lostReportService.getLostReportPhoto(id, authentication.getToken())
                .map(this::photoResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/photo-url")
    public ResponseEntity<PhotoUrlResponse> getLostReportPhotoUrl(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return lostReportService.getLostReportPhotoUrl(
                        id,
                        authentication == null ? null : authentication.getToken()
                )
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<InputStreamResource> photoResponse(PhotoData photo) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .contentLength(photo.sizeBytes())
                .body(new InputStreamResource(photo.content()));
    }
}

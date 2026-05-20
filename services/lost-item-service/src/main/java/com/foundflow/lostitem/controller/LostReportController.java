package com.foundflow.lostitem.controller;

import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.CountResponse;
import com.foundflow.lostitem.dto.HistogramResponse;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.domain.ReportStatus;
import com.foundflow.lostitem.service.LostReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
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

    @PostMapping
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
}

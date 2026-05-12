package com.foundflow.lostitem.controller;

import com.foundflow.lostitem.dto.CreateLostReportRequest;
import com.foundflow.lostitem.dto.LostReportResponse;
import com.foundflow.lostitem.dto.UpdateLostReportRequest;
import com.foundflow.lostitem.service.LostReportService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lost-reports")
public class LostReportController {

    private final LostReportService lostReportService;

    public LostReportController(LostReportService lostReportService) {
        this.lostReportService = lostReportService;
    }

    @PostMapping
    public ResponseEntity<LostReportResponse> createLostReport(
            @Valid @RequestBody CreateLostReportRequest request
    ) {
        LostReportResponse response = lostReportService.createLostReport(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<LostReportResponse>> getAllLostReports() {
        return ResponseEntity.ok(lostReportService.getAllLostReports());
    }

    @GetMapping("/{id}")
    public ResponseEntity<LostReportResponse> getLostReportById(@PathVariable UUID id) {
        return lostReportService.getLostReportById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<LostReportResponse> updateLostReport(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateLostReportRequest request
    ) {
        return lostReportService.updateLostReport(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
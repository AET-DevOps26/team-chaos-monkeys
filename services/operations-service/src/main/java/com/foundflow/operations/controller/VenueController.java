package com.foundflow.operations.controller;

import com.foundflow.operations.dto.CreateVenueRequest;
import com.foundflow.operations.dto.PublicVenueResponse;
import com.foundflow.operations.dto.UpdateVenueRequest;
import com.foundflow.operations.dto.VenueKpiResponse;
import com.foundflow.operations.dto.VenueResponse;
import com.foundflow.operations.service.VenueKpiService;
import com.foundflow.operations.service.VenueService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.net.URI;

@RestController
@RequestMapping("/api/venues")
public class VenueController {

    private final VenueService venueService;
    private final VenueKpiService venueKpiService;

    public VenueController(
            VenueService venueService,
            VenueKpiService venueKpiService
    ) {
        this.venueService = venueService;
        this.venueKpiService = venueKpiService;
    }

    @PostMapping
    public ResponseEntity<VenueResponse> createVenue(
            @Valid @RequestBody CreateVenueRequest request,
            JwtAuthenticationToken authentication
    ) {
        VenueResponse response = venueService.createVenue(request, authentication.getToken());
        return ResponseEntity
        .created(URI.create("/api/venues/" + response.id()))
        .body(response);
    }

    @GetMapping
    public ResponseEntity<List<VenueResponse>> getAllVenues(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(venueService.getAllVenues(authentication.getToken()));
    }

    @GetMapping("/public")
    public ResponseEntity<List<PublicVenueResponse>> getPublicVenues() {
        return ResponseEntity.ok(venueService.getPublicVenues());
    }

    @GetMapping("/kpis")
    public ResponseEntity<VenueKpiResponse> getVenueKpis(
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        if (venueId == null) {
            return ResponseEntity.ok(venueKpiService.getKpis(authentication.getToken()));
        }

        return venueService.getVenueById(venueId, authentication.getToken())
                .map(venue -> ResponseEntity.ok(venueKpiService.getKpis(
                        venueId,
                        authentication.getToken()
                )))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<VenueResponse> getVenueById(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return venueService.getVenueById(id, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<VenueResponse> updateVenue(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateVenueRequest request,
            JwtAuthenticationToken authentication
    ) {
        return venueService.updateVenue(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteVenue(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        if (venueService.deleteVenue(id, authentication.getToken())) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.notFound().build();
    }
}

package com.foundflow.pickup.controller;

import com.foundflow.pickup.dto.CreatePickupRequest;
import com.foundflow.pickup.dto.CreatePickupScheduleRequest;
import com.foundflow.pickup.dto.PickupEmailLogResponse;
import com.foundflow.pickup.dto.PickupResponse;
import com.foundflow.pickup.dto.PickupScheduleResponse;
import com.foundflow.pickup.dto.PickupSlotResponse;
import com.foundflow.pickup.dto.PublicPickupResponse;
import com.foundflow.pickup.dto.StaffPickupRequest;
import com.foundflow.pickup.dto.UpdatePickupRequest;
import com.foundflow.pickup.dto.UpdatePickupScheduleRequest;
import com.foundflow.pickup.service.PickupService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pickups")
public class PickupController {

    private final PickupService pickupService;

    public PickupController(PickupService pickupService) {
        this.pickupService = pickupService;
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<List<PickupSlotResponse>> getPublicSlots(@PathVariable String token) {
        return ResponseEntity.ok(pickupService.getPublicSlots(token));
    }

    @PostMapping("/public/{token}")
    public ResponseEntity<PublicPickupResponse> createPublicPickup(
            @PathVariable String token,
            @Valid @RequestBody CreatePickupRequest request
    ) {
        PublicPickupResponse response = pickupService.createPublicPickup(token, request);
        return ResponseEntity
                .created(URI.create("/api/pickups/" + response.id()))
                .body(response);
    }

    @PutMapping("/public/{token}")
    public ResponseEntity<PublicPickupResponse> updatePublicPickup(
            @PathVariable String token,
            @Valid @RequestBody UpdatePickupRequest request
    ) {
        return pickupService.updatePublicPickup(token, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/public/{token}")
    public ResponseEntity<Void> deletePublicPickup(@PathVariable String token) {
        if (pickupService.deletePublicPickup(token)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping
    public ResponseEntity<List<PickupResponse>> getPickups(
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(pickupService.getPickups(venueId, authentication.getToken()));
    }

    @PutMapping("/matches/{matchId}")
    public ResponseEntity<PickupResponse> upsertPickupForMatch(
            @PathVariable UUID matchId,
            @Valid @RequestBody StaffPickupRequest request,
            JwtAuthenticationToken authentication
    ) {
        return pickupService.upsertPickupForMatch(matchId, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{pickupId}")
    public ResponseEntity<PickupResponse> updatePickup(
            @PathVariable UUID pickupId,
            @Valid @RequestBody StaffPickupRequest request,
            JwtAuthenticationToken authentication
    ) {
        return pickupService.updatePickup(pickupId, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/matches/{matchId}")
    public ResponseEntity<Void> deletePickupForMatch(
            @PathVariable UUID matchId,
            JwtAuthenticationToken authentication
    ) {
        if (pickupService.deletePickupForMatch(matchId, authentication.getToken())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{pickupId}")
    public ResponseEntity<Void> deletePickup(
            @PathVariable UUID pickupId,
            JwtAuthenticationToken authentication
    ) {
        if (pickupService.deletePickup(pickupId, authentication.getToken())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/schedule")
    public ResponseEntity<List<PickupScheduleResponse>> getSchedules(
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(pickupService.getSchedules(venueId, authentication.getToken()));
    }

    @PostMapping("/schedule")
    public ResponseEntity<PickupScheduleResponse> createSchedule(
            @Valid @RequestBody CreatePickupScheduleRequest request,
            JwtAuthenticationToken authentication
    ) {
        PickupScheduleResponse response = pickupService.createSchedule(request, authentication.getToken());
        return ResponseEntity
                .created(URI.create("/api/pickups/schedule/" + response.id()))
                .body(response);
    }

    @PutMapping("/schedule/{scheduleId}")
    public ResponseEntity<PickupScheduleResponse> updateSchedule(
            @PathVariable UUID scheduleId,
            @Valid @RequestBody UpdatePickupScheduleRequest request,
            JwtAuthenticationToken authentication
    ) {
        return pickupService.updateSchedule(scheduleId, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/schedule/{scheduleId}")
    public ResponseEntity<Void> deleteSchedule(
            @PathVariable UUID scheduleId,
            JwtAuthenticationToken authentication
    ) {
        if (pickupService.deleteSchedule(scheduleId, authentication.getToken())) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/email-log")
    public ResponseEntity<List<PickupEmailLogResponse>> getEmailLog(
            @RequestParam(required = false) String recipient,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(pickupService.getEmailLog(recipient, authentication.getToken()));
    }
}

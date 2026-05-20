package com.foundflow.notification.controller;

import com.foundflow.notification.dto.CreateNotificationRequest;
import com.foundflow.notification.dto.NotificationBlueprintResponse;
import com.foundflow.notification.dto.NotificationResponse;
import com.foundflow.notification.dto.UpdateNotificationRequest;
import com.foundflow.notification.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.net.URI;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PostMapping
    public ResponseEntity<NotificationResponse> createNotification(
            @Valid @RequestBody CreateNotificationRequest request,
            JwtAuthenticationToken authentication
    ) {
        NotificationResponse response = notificationService.createNotification(request, authentication.getToken());
        return ResponseEntity
                .created(URI.create("/api/notifications/" + response.id()))
                .body(response);
    }

    @GetMapping
    public ResponseEntity<List<NotificationResponse>> getAllNotifications(
            @RequestParam(required = false) String email,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(notificationService.getAllNotifications(email, authentication.getToken()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NotificationResponse> getNotificationById(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return notificationService.getNotificationById(id, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<NotificationResponse> updateNotification(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateNotificationRequest request,
            JwtAuthenticationToken authentication
    ) {
        return notificationService.updateNotification(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/bluePrints")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_MANAGER')")
    public ResponseEntity<Void> createNotificationBlueprint(
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.accepted().build();
    }

    @GetMapping("/bluePrints")
    public ResponseEntity<List<NotificationBlueprintResponse>> getNotificationBlueprints() {
        return ResponseEntity.ok(List.of(dummyBlueprint()));
    }

    @GetMapping("/bluePrints/{id}")
    public ResponseEntity<NotificationBlueprintResponse> getNotificationBlueprintById(
            @PathVariable UUID id
    ) {
        return ResponseEntity.ok(new NotificationBlueprintResponse(
                id,
                "default-lost-item-match",
                "de",
                "Wir haben moeglicherweise Ihr verlorenes Objekt gefunden",
                "Gute Nachrichten",
                "Bitte wenden Sie sich an das Fundbuero."
        ));
    }

    @PutMapping("/bluePrints/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'OPS_MANAGER')")
    public ResponseEntity<Void> updateNotificationBlueprint(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.accepted().build();
    }

    private NotificationBlueprintResponse dummyBlueprint() {
        return new NotificationBlueprintResponse(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "default-lost-item-match",
                "de",
                "Wir haben moeglicherweise Ihr verlorenes Objekt gefunden",
                "Gute Nachrichten",
                "Bitte wenden Sie sich an das Fundbuero."
        );
    }
}

package com.foundflow.founditem.controller;

import com.foundflow.founditem.dto.CreateFoundItemRequest;
import com.foundflow.founditem.dto.FoundItemResponse;
import com.foundflow.founditem.dto.UpdateFoundItemRequest;
import com.foundflow.founditem.service.FoundItemService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
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

    @PostMapping
    public ResponseEntity<FoundItemResponse> createFoundItem(
            @Valid @RequestBody CreateFoundItemRequest request
    ) {
        FoundItemResponse response = foundItemService.createFoundItem(request);
        return ResponseEntity
        .created(URI.create("/api/found-items/" + response.id()))
        .body(response);
    }

    @GetMapping
    public ResponseEntity<List<FoundItemResponse>> getAllFoundItems() {
        return ResponseEntity.ok(foundItemService.getAllFoundItems());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FoundItemResponse> getFoundItemById(@PathVariable UUID id) {
        return foundItemService.getFoundItemById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<FoundItemResponse> updateFoundItem(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateFoundItemRequest request
    ) {
        return foundItemService.updateFoundItem(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
package com.foundflow.founditem.controller;

import com.foundflow.founditem.dto.PublicFoundItemResponse;
import com.foundflow.founditem.service.FoundItemService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/internal/found-items")
public class InternalFoundItemController {

    private final FoundItemService foundItemService;
    private final String internalToken;

    public InternalFoundItemController(
            FoundItemService foundItemService,
            @Value("${foundflow.internal.token}") String internalToken
    ) {
        this.foundItemService = foundItemService;
        if (internalToken == null || internalToken.isBlank()) {
            throw new IllegalStateException("foundflow.internal.token must be configured.");
        }
        this.internalToken = internalToken;
    }

    @GetMapping("/{id}/public-detail")
    public ResponseEntity<PublicFoundItemResponse> getPublicFoundItemDetail(
            @PathVariable UUID id,
            @RequestParam UUID venueId,
            @RequestHeader(name = "X-FoundFlow-Internal-Token", required = false) String requestToken
    ) {
        verifyInternalToken(requestToken);
        return foundItemService.getPublicFoundItemDetail(id, venueId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private void verifyInternalToken(String requestToken) {
        if (!internalToken.equals(requestToken)) {
            throw new AccessDeniedException("Invalid internal request token.");
        }
    }
}

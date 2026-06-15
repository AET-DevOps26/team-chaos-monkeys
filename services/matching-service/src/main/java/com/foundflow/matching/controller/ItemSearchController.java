package com.foundflow.matching.controller;

import com.foundflow.matching.dto.ItemSearchRequest;
import com.foundflow.matching.dto.ItemSearchResponse;
import com.foundflow.matching.service.ItemSearchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Staff free-text item search (RAG over the pgvector index). Lives under {@code /api/matches}
 * because matching-service owns the vectors, but it searches the item corpus, not Match entities.
 */
@RestController
@RequestMapping("/api/matches")
public class ItemSearchController {

    private final ItemSearchService itemSearchService;

    public ItemSearchController(ItemSearchService itemSearchService) {
        this.itemSearchService = itemSearchService;
    }

    @PostMapping("/search")
    public ResponseEntity<ItemSearchResponse> search(
            @Valid @RequestBody ItemSearchRequest request,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(itemSearchService.search(request, authentication.getToken()));
    }
}

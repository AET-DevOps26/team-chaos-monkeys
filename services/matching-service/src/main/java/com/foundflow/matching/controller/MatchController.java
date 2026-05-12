package com.foundflow.matching.controller;

import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<MatchResponse> createMatch(
            @Valid @RequestBody CreateMatchRequest request
    ) {
        MatchResponse response = matchService.createMatch(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getAllMatches() {
        return ResponseEntity.ok(matchService.getAllMatches());
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatchById(@PathVariable UUID id) {
        return matchService.getMatchById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MatchResponse> updateMatch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMatchRequest request
    ) {
        return matchService.updateMatch(id, request)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
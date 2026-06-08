package com.foundflow.matching.controller;

import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.CreatePublicMatchLinkRequest;
import com.foundflow.matching.dto.CountResponse;
import com.foundflow.matching.dto.HistogramResponse;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.PublicFoundItemResponse;
import com.foundflow.matching.dto.PublicMatchLinkResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.net.URI;

@RestController
@RequestMapping("/api/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PostMapping
    public ResponseEntity<MatchResponse> createMatch(
            @Valid @RequestBody CreateMatchRequest request,
            JwtAuthenticationToken authentication
    ) {
        MatchResponse response = matchService.createMatch(request, authentication.getToken());
        return ResponseEntity
        .created(URI.create("/api/matches/" + response.id()))
        .body(response);
    }

    @GetMapping
    public ResponseEntity<List<MatchResponse>> getAllMatches(
            @RequestParam(required = false, name = "foundItem") UUID foundItemId,
            @RequestParam(required = false, name = "lostItem") UUID lostReportId,
            @RequestParam(required = false) MatchStatus status,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(matchService.getAllMatches(
                foundItemId,
                lostReportId,
                status,
                authentication.getToken()
        ));
    }

    @GetMapping("/count")
    public ResponseEntity<CountResponse> countMatches(
            @RequestParam(required = false, name = "foundItem") UUID foundItemId,
            @RequestParam(required = false, name = "lostItem") UUID lostReportId,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(new CountResponse(
                matchService.countMatches(
                        foundItemId,
                        lostReportId,
                        status,
                        venueId,
                        authentication.getToken()
                )
        ));
    }

    @GetMapping("/histogram")
    public ResponseEntity<HistogramResponse> getMatchHistogram(
            @RequestParam(required = false, name = "foundItem") UUID foundItemId,
            @RequestParam(required = false, name = "lostItem") UUID lostReportId,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) UUID venueId,
            JwtAuthenticationToken authentication
    ) {
        return ResponseEntity.ok(matchService.getMatchHistogram(
                foundItemId,
                lostReportId,
                status,
                venueId,
                authentication.getToken()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<MatchResponse> getMatchById(
            @PathVariable UUID id,
            JwtAuthenticationToken authentication
    ) {
        return matchService.getMatchById(id, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<MatchResponse> updateMatch(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateMatchRequest request,
            JwtAuthenticationToken authentication
    ) {
        return matchService.updateMatch(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/public-link")
    public ResponseEntity<PublicMatchLinkResponse> createPublicMatchLink(
            @PathVariable UUID id,
            @Valid @RequestBody CreatePublicMatchLinkRequest request,
            JwtAuthenticationToken authentication
    ) {
        return matchService.createPublicMatchLink(id, request, authentication.getToken())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<MatchResponse> getPublicMatch(@PathVariable String token) {
        return matchService.getPublicMatch(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/public/{token}/found-item")
    public ResponseEntity<PublicFoundItemResponse> getPublicFoundItem(@PathVariable String token) {
        return matchService.getPublicFoundItem(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/public/match-links/{token}/confirm")
    public ResponseEntity<MatchResponse> confirmPublicMatch(@PathVariable String token) {
        return matchService.confirmPublicMatch(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/public/match-links/{token}/reject")
    public ResponseEntity<MatchResponse> rejectPublicMatch(@PathVariable String token) {
        return matchService.rejectPublicMatch(token)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

}

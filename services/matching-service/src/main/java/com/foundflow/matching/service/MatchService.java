package com.foundflow.matching.service;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.repository.MatchRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class MatchService {

    private final MatchRepository matchRepository;

    public MatchService(MatchRepository matchRepository) {
        this.matchRepository = matchRepository;
    }

    public MatchResponse createMatch(CreateMatchRequest request) {
        Match match = new Match(
                request.foundItemId(),
                request.lostReportId(),
                request.attributeScore(),
                request.semanticScore(),
                request.combinedScore(),
                LocalDateTime.now()
        );

        Match savedMatch = matchRepository.save(match);
        return toResponse(savedMatch);
    }

    public List<MatchResponse> getAllMatches() {
        return matchRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public Optional<MatchResponse> getMatchById(UUID id) {
        return matchRepository.findById(id)
                .map(this::toResponse);
    }

    public Optional<MatchResponse> updateMatch(
            UUID id,
            UpdateMatchRequest request
    ) {
        return matchRepository.findById(id)
                .map(match -> {
                    match.setFoundItemId(request.foundItemId());
                    match.setLostReportId(request.lostReportId());
                    match.setAttributeScore(request.attributeScore());
                    match.setSemanticScore(request.semanticScore());
                    match.setCombinedScore(request.combinedScore());

                    Match updatedMatch = matchRepository.save(match);
                    return toResponse(updatedMatch);
                });
    }

    private MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getFoundItemId(),
                match.getLostReportId(),
                match.getAttributeScore(),
                match.getSemanticScore(),
                match.getCombinedScore(),
                match.getCreatedAt()
        );
    }
}
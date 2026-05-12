package com.foundflow.matching.service;

import com.foundflow.matching.domain.Match;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.repository.MatchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Test
    void createMatch_shouldSaveAndReturnMatch() {
        MatchService matchService = new MatchService(matchRepository);

        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        CreateMatchRequest request = new CreateMatchRequest(
                foundItemId,
                lostReportId,
                0.75f,
                0.90f,
                0.84f
        );

        when(matchRepository.save(any(Match.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        MatchResponse response = matchService.createMatch(request);

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());

        Match savedMatch = captor.getValue();

        assertEquals(foundItemId, savedMatch.getFoundItemId());
        assertEquals(lostReportId, savedMatch.getLostReportId());
        assertEquals(0.75f, savedMatch.getAttributeScore());
        assertEquals(0.90f, savedMatch.getSemanticScore());
        assertEquals(0.84f, savedMatch.getCombinedScore());
        assertNotNull(savedMatch.getCreatedAt());

        assertEquals(foundItemId, response.foundItemId());
        assertEquals(lostReportId, response.lostReportId());
        assertEquals(0.84f, response.combinedScore());
        assertNotNull(response.createdAt());
    }

    @Test
    void getMatchById_shouldReturnResponseWhenMatchExists() {
        MatchService matchService = new MatchService(matchRepository);

        UUID id = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        Match match = new Match(
                foundItemId,
                lostReportId,
                0.70f,
                0.85f,
                0.79f,
                java.time.LocalDateTime.now()
        );

        when(matchRepository.findById(id)).thenReturn(Optional.of(match));

        Optional<MatchResponse> response = matchService.getMatchById(id);

        assertTrue(response.isPresent());
        assertEquals(foundItemId, response.get().foundItemId());
        assertEquals(lostReportId, response.get().lostReportId());
        assertEquals(0.79f, response.get().combinedScore());

        verify(matchRepository).findById(id);
    }

    @Test
    void getMatchById_shouldReturnEmptyWhenMatchDoesNotExist() {
        MatchService matchService = new MatchService(matchRepository);

        UUID id = UUID.randomUUID();

        when(matchRepository.findById(id)).thenReturn(Optional.empty());

        Optional<MatchResponse> response = matchService.getMatchById(id);

        assertTrue(response.isEmpty());
        verify(matchRepository).findById(id);
    }

    @Test
    void getAllMatches_shouldReturnMappedResponses() {
        MatchService matchService = new MatchService(matchRepository);

        Match match1 = new Match(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0.60f,
                0.80f,
                0.72f,
                java.time.LocalDateTime.now()
        );

        Match match2 = new Match(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0.90f,
                0.95f,
                0.93f,
                java.time.LocalDateTime.now()
        );

        when(matchRepository.findAll()).thenReturn(List.of(match1, match2));

        List<MatchResponse> responses = matchService.getAllMatches();

        assertEquals(2, responses.size());
        assertEquals(0.72f, responses.get(0).combinedScore());
        assertEquals(0.93f, responses.get(1).combinedScore());

        verify(matchRepository).findAll();
    }

    @Test
    void updateMatch_shouldUpdateExistingMatch() {
        MatchService matchService = new MatchService(matchRepository);

        UUID id = UUID.randomUUID();

        Match existingMatch = new Match(
                UUID.randomUUID(),
                UUID.randomUUID(),
                0.50f,
                0.60f,
                0.55f,
                java.time.LocalDateTime.now()
        );

        UUID newFoundItemId = UUID.randomUUID();
        UUID newLostReportId = UUID.randomUUID();

        UpdateMatchRequest request = new UpdateMatchRequest(
                newFoundItemId,
                newLostReportId,
                0.88f,
                0.91f,
                0.90f
        );

        when(matchRepository.findById(id)).thenReturn(Optional.of(existingMatch));
        when(matchRepository.save(any(Match.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Optional<MatchResponse> response = matchService.updateMatch(id, request);

        assertTrue(response.isPresent());
        assertEquals(newFoundItemId, response.get().foundItemId());
        assertEquals(newLostReportId, response.get().lostReportId());
        assertEquals(0.88f, response.get().attributeScore());
        assertEquals(0.91f, response.get().semanticScore());
        assertEquals(0.90f, response.get().combinedScore());

        verify(matchRepository).findById(id);
        verify(matchRepository).save(existingMatch);
    }
}
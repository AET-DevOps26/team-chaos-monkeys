package com.foundflow.matching.service;

import com.foundflow.matching.client.FoundItemClient;
import com.foundflow.matching.client.ItemVenueReference;
import com.foundflow.matching.client.LostItemClient;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.repository.BucketCountView;
import com.foundflow.matching.repository.MatchEmailLogRepository;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FoundItemClient foundItemClient;

    @Mock
    private LostItemClient lostItemClient;

    @Mock
    private MagicLinkService magicLinkService;

    @Mock
    private MatchEmailSender matchEmailSender;

    @Mock
    private MatchEmailLogRepository matchEmailLogRepository;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createMatch_shouldSaveAndReturnMatch() {
        MatchService matchService = matchService();

        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        CreateMatchRequest request = new CreateMatchRequest(
                foundItemId,
                lostReportId,
                UUID.randomUUID(),
                0.75f,
                0.90f,
                0.84f
        );

        when(matchRepository.save(any(Match.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, venueId));

        MatchResponse response = matchService.createMatch(request, staffJwt(venueId));

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());

        Match savedMatch = captor.getValue();

        assertEquals(foundItemId, savedMatch.getFoundItemId());
        assertEquals(lostReportId, savedMatch.getLostReportId());
        assertEquals(venueId, savedMatch.getVenueId());
        assertEquals(MatchStatus.PENDING, savedMatch.getStatus());
        assertEquals(0.75f, savedMatch.getAttributeScore());
        assertEquals(0.90f, savedMatch.getSemanticScore());
        assertEquals(0.84f, savedMatch.getCombinedScore());
        assertNotNull(savedMatch.getCreatedAt());

        assertEquals(foundItemId, response.foundItemId());
        assertEquals(lostReportId, response.lostReportId());
        assertEquals(venueId, response.venueId());
        assertEquals(MatchStatus.PENDING, response.status());
        assertEquals(0.84f, response.combinedScore());
        assertNotNull(response.createdAt());
    }

    @Test
    void getMatchById_shouldReturnResponseWhenMatchExists() {
        MatchService matchService = matchService();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        Match match = new Match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                0.70f,
                0.85f,
                0.79f,
                LocalDateTime.now()
        );

        when(matchRepository.findById(id)).thenReturn(Optional.of(match));

        Optional<MatchResponse> response = matchService.getMatchById(id, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(foundItemId, response.get().foundItemId());
        assertEquals(lostReportId, response.get().lostReportId());
        assertEquals(MatchStatus.PENDING, response.get().status());
        assertEquals(0.79f, response.get().combinedScore());

        verify(matchRepository).findById(id);
    }

    @Test
    void getMatchById_shouldReturnEmptyWhenMatchDoesNotExist() {
        MatchService matchService = matchService();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(matchRepository.findById(id)).thenReturn(Optional.empty());

        Optional<MatchResponse> response = matchService.getMatchById(id, staffJwt(venueId));

        assertTrue(response.isEmpty());
        verify(matchRepository).findById(id);
    }

    @Test
    void getAllMatches_shouldReturnMappedResponsesForOwnVenue() {
        MatchService matchService = matchService();

        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();

        Match match1 = new Match(
                foundItemId,
                lostReportId,
                venueId,
                MatchStatus.PENDING,
                0.60f,
                0.80f,
                0.72f,
                LocalDateTime.now()
        );

        Match match2 = new Match(
                UUID.randomUUID(),
                UUID.randomUUID(),
                venueId,
                MatchStatus.CONFIRMED,
                0.90f,
                0.95f,
                0.93f,
                LocalDateTime.now()
        );

        when(matchRepository.findFiltered(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        )).thenReturn(List.of(match1));

        List<MatchResponse> responses =
                matchService.getAllMatches(
                        foundItemId,
                        lostReportId,
                        MatchStatus.PENDING,
                        staffJwt(venueId)
                );

        assertEquals(1, responses.size());
        assertEquals(0.72f, responses.get(0).combinedScore());
        assertEquals(MatchStatus.PENDING, responses.get(0).status());

        verify(matchRepository).findFiltered(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        );
    }

    @Test
    void countAndHistogram_shouldUseCombinedFilters() {
        MatchService matchService = matchService();

        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        when(matchRepository.countFiltered(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        )).thenReturn(1L);
        when(matchRepository.findDailyBuckets(
                venueId,
                foundItemId,
                lostReportId,
                MatchStatus.PENDING.name()
        )).thenReturn(List.of(bucket(java.time.LocalDate.of(2026, 5, 19), 1)));

        long count = matchService.countMatches(
                foundItemId,
                lostReportId,
                MatchStatus.PENDING,
                staffJwt(venueId)
        );
        var histogram = matchService.getMatchHistogram(
                foundItemId,
                lostReportId,
                MatchStatus.PENDING,
                staffJwt(venueId)
        );

        assertEquals(1, count);
        assertEquals(1, histogram.perDay().size());
        assertEquals(java.time.LocalDate.of(2026, 5, 19), histogram.perDay().get(0).bucketStart());
        assertEquals(1, histogram.perDay().get(0).count());
        assertEquals(java.time.LocalDate.of(2026, 5, 18), histogram.perWeek().get(0).bucketStart());
        assertEquals(java.time.LocalDate.of(2026, 5, 1), histogram.perMonth().get(0).bucketStart());
    }

    @Test
    void updateMatch_shouldUpdateExistingMatch() {
        MatchService matchService = matchService();

        UUID id = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        Match existingMatch = new Match(
                UUID.randomUUID(),
                UUID.randomUUID(),
                venueId,
                MatchStatus.PENDING,
                0.50f,
                0.60f,
                0.55f,
                LocalDateTime.now()
        );

        UUID newFoundItemId = UUID.randomUUID();
        UUID newLostReportId = UUID.randomUUID();

        UpdateMatchRequest request = new UpdateMatchRequest(
                newFoundItemId,
                newLostReportId,
                UUID.randomUUID(),
                MatchStatus.CONFIRMED,
                0.88f,
                0.91f,
                0.90f
        );

        when(matchRepository.findById(id)).thenReturn(Optional.of(existingMatch));
        when(matchRepository.save(any(Match.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(foundItemClient.getFoundItem(eq(newFoundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(newFoundItemId, venueId));
        when(lostItemClient.getLostItem(eq(newLostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(newLostReportId, venueId));

        Optional<MatchResponse> response =
                matchService.updateMatch(id, request, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals(newFoundItemId, response.get().foundItemId());
        assertEquals(newLostReportId, response.get().lostReportId());
        assertEquals(venueId, response.get().venueId());
        assertEquals(MatchStatus.CONFIRMED, response.get().status());
        assertEquals(0.88f, response.get().attributeScore());
        assertEquals(0.91f, response.get().semanticScore());
        assertEquals(0.90f, response.get().combinedScore());

        verify(matchRepository).findById(id);
        verify(matchRepository).save(existingMatch);
    }

    @Test
    void createMatch_shouldRejectResourcesFromDifferentVenues() {
        MatchService matchService = matchService();

        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        CreateMatchRequest request = new CreateMatchRequest(
                foundItemId,
                lostReportId,
                venueId,
                0.75f,
                0.90f,
                0.84f
        );

        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, UUID.randomUUID()));

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> matchService.createMatch(request, staffJwt(venueId))
        );

        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void createPublicMatchLink_shouldReturnMatchAndPickupUrls() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING, LocalDateTime.now());
        ReflectionTestUtils.setField(match, "id", matchId);
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(magicLinkService.createMatchViewToken(matchId, venueId, "lost@example.com"))
                .thenReturn("public-token");

        var response = matchService.createPublicMatchLink(
                matchId,
                new com.foundflow.matching.dto.CreatePublicMatchLinkRequest("lost@example.com"),
                staffJwt(venueId)
        );

        assertTrue(response.isPresent());
        assertEquals("public-token", response.get().token());
        assertEquals("http://localhost:8080/api/matches/public/public-token", response.get().matchUrl());
        assertEquals("http://localhost:8080/api/pickups/public/public-token", response.get().pickupUrl());
        verify(matchEmailSender).sendPublicMatchLink(
                "lost@example.com",
                venueId,
                matchId,
                "http://localhost:8080/api/matches/public/public-token"
        );
    }

    @Test
    void confirmPublicMatch_shouldUpdateLinkedMatchOnly() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING, LocalDateTime.now());
        when(magicLinkService.verifyMatchViewToken("public-token"))
                .thenReturn(new MagicLinkClaims("match_view", matchId, null, venueId, "lost@example.com", 1L));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        var response = matchService.confirmPublicMatch("public-token");

        assertTrue(response.isPresent());
        assertEquals(MatchStatus.CONFIRMED, response.get().status());
        verify(matchRepository).save(match);
    }

    private MatchService matchService() {
        return new MatchService(
                matchRepository,
                venueAccessService,
                foundItemClient,
                lostItemClient,
                magicLinkService,
                matchEmailSender,
                matchEmailLogRepository,
                "http://localhost:8080"
        );
    }

    private Jwt staffJwt(UUID venueId) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("roles", List.of("STAFF"))
                .claim("venue_id", venueId.toString())
                .build();
    }

    private BucketCountView bucket(java.time.LocalDate bucketStart, long count) {
        return new BucketCountView() {
            @Override
            public java.time.LocalDate getBucketStart() {
                return bucketStart;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private Match match(
            UUID foundItemId,
            UUID lostReportId,
            UUID venueId,
            MatchStatus status,
            LocalDateTime createdAt
    ) {
        return new Match(
                foundItemId,
                lostReportId,
                venueId,
                status,
                0.60f,
                0.80f,
                0.72f,
                createdAt
        );
    }
}

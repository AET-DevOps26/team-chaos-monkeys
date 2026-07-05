package com.foundflow.matching.service;

import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.magiclink.MagicLinkClaims;
import com.foundflow.magiclink.MagicLinkService;
import com.foundflow.matching.client.FoundItemClient;
import com.foundflow.matching.client.ItemVenueReference;
import com.foundflow.matching.client.LostItemClient;
import com.foundflow.matching.client.LostReportContactReference;
import com.foundflow.matching.client.RemotePhoto;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.CreatePublicMatchLinkRequest;
import com.foundflow.matching.dto.PublicFoundItemResponse;
import com.foundflow.matching.messaging.MatchInviteEventPublisher;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.security.VenueAccessService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
    private MatchInviteEventPublisher matchInviteEventPublisher;

    private final VenueAccessService venueAccessService = new VenueAccessService();

    @Test
    void createMatch_shouldSaveAndReturnMatch() {
        MatchService matchService = matchService();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, venueId));
        when(matchRepository.save(any(Match.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = matchService.createMatch(
                new CreateMatchRequest(foundItemId, lostReportId, UUID.randomUUID(), 0.75f, 0.90f, 0.84f),
                staffJwt(venueId)
        );

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        Match savedMatch = captor.getValue();
        assertEquals(foundItemId, savedMatch.getFoundItemId());
        assertEquals(lostReportId, savedMatch.getLostReportId());
        assertEquals(venueId, savedMatch.getVenueId());
        assertEquals(MatchStatus.PENDING, savedMatch.getStatus());
        assertNotNull(savedMatch.getCreatedAt());
        assertEquals(0.84f, response.combinedScore());
    }

    @Test
    void createMatch_shouldReturnExistingPendingMatch_whenAutoPipelineAlreadyCreatedOne() {
        MatchService matchService = matchService();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID existingId = UUID.randomUUID();
        Match existing = match(foundItemId, lostReportId, venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(existing, "id", existingId);

        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, venueId));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.of(existing));

        var response = matchService.createMatch(
                new CreateMatchRequest(foundItemId, lostReportId, UUID.randomUUID(), 0.75f, 0.90f, 0.84f),
                staffJwt(venueId)
        );

        assertEquals(existingId, response.id());
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void createMatch_shouldReject409_whenExistingMatchIsNotPending() {
        MatchService matchService = matchService();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match existing = match(foundItemId, lostReportId, venueId, MatchStatus.CONFIRMED);

        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, venueId));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.of(existing));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> matchService.createMatch(
                        new CreateMatchRequest(foundItemId, lostReportId, UUID.randomUUID(), 0.75f, 0.90f, 0.84f),
                        staffJwt(venueId)
                )
        );

        assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void createMatch_shouldRejectResourcesFromDifferentVenues() {
        MatchService matchService = matchService();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(foundItemClient.getFoundItem(eq(foundItemId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(foundItemId, venueId));
        when(lostItemClient.getLostItem(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new ItemVenueReference(lostReportId, UUID.randomUUID()));

        assertThrows(
                ResponseStatusException.class,
                () -> matchService.createMatch(
                        new CreateMatchRequest(foundItemId, lostReportId, venueId, 0.75f, 0.90f, 0.84f),
                        staffJwt(venueId)
                )
        );
        verify(matchRepository, never()).save(any(Match.class));
    }

    @Test
    void createPublicMatchLink_shouldPublishInviteAndReturnMatchAndPickupUrls() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(magicLinkService.createMatchViewToken(matchId, venueId, "lost@example.com"))
                .thenReturn("public-token");

        var response = matchService.createPublicMatchLink(
                matchId,
                new CreatePublicMatchLinkRequest("lost@example.com"),
                staffJwt(venueId)
        );

        assertTrue(response.isPresent());
        assertEquals("public-token", response.get().token());
        assertEquals("http://localhost:8080/report/match/public-token", response.get().matchUrl());
        assertEquals("http://localhost:8080/api/pickups/public/public-token", response.get().pickupUrl());
        assertEquals("public-token", match.getPublicLinkToken());
        assertEquals("lost@example.com", match.getPublicLinkRecipientEmail());
        assertNotNull(match.getPublicLinkIssuedAt());
        verify(matchInviteEventPublisher).publishMatchInviteRequested(
                matchId,
                "lost@example.com",
                venueId,
                "http://localhost:8080/report/match/public-token"
        );
    }

    @Test
    void createPublicMatchLink_shouldReturnExistingLinkWithoutPublishingDuplicate() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);
        match.setRecipientEmail("lost@example.com");
        match.setPublicLinkToken("existing-token");
        match.setPublicLinkRecipientEmail("lost@example.com");
        match.setPublicLinkIssuedAt(LocalDateTime.now().minusMinutes(5));

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        var response = matchService.createPublicMatchLink(
                matchId,
                new CreatePublicMatchLinkRequest("lost@example.com"),
                staffJwt(venueId)
        );

        assertTrue(response.isPresent());
        assertEquals("existing-token", response.get().token());
        assertEquals("http://localhost:8080/report/match/existing-token", response.get().matchUrl());
        assertEquals("http://localhost:8080/api/pickups/public/existing-token", response.get().pickupUrl());
        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(magicLinkService, matchInviteEventPublisher);
    }

    @Test
    void createPublicMatchLink_withoutEmail_shouldDefaultToLostReportContactEmail() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), lostReportId, venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(lostItemClient.getLostReportContact(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new LostReportContactReference(lostReportId, venueId, "Guest@Example.com"));
        when(matchRepository.save(match)).thenReturn(match);
        when(magicLinkService.createMatchViewToken(matchId, venueId, "guest@example.com"))
                .thenReturn("public-token");

        var response = matchService.createPublicMatchLink(matchId, null, staffJwt(venueId));

        assertTrue(response.isPresent());
        assertEquals("public-token", response.get().token());
        assertEquals("guest@example.com", match.getRecipientEmail());
        verify(matchInviteEventPublisher).publishMatchInviteRequested(
                matchId,
                "guest@example.com",
                venueId,
                "http://localhost:8080/report/match/public-token"
        );
    }

    @Test
    void createPublicMatchLink_withoutRecipient_shouldReject400() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), lostReportId, venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(lostItemClient.getLostReportContact(eq(lostReportId), any(Jwt.class)))
                .thenReturn(new LostReportContactReference(lostReportId, venueId, null));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> matchService.createPublicMatchLink(matchId, null, staffJwt(venueId))
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verifyNoInteractions(magicLinkService, matchInviteEventPublisher);
    }

    @Test
    void createAutomaticPublicMatchLink_shouldPublishForPendingMatchWithRecipient() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);
        match.setRecipientEmail("guest@example.com");

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(magicLinkService.createMatchViewToken(matchId, venueId, "guest@example.com"))
                .thenReturn("public-token");

        var response = matchService.createAutomaticPublicMatchLink(matchId, null);

        assertTrue(response.isPresent());
        verify(matchInviteEventPublisher).publishMatchInviteRequested(
                matchId,
                "guest@example.com",
                venueId,
                "http://localhost:8080/report/match/public-token"
        );
    }

    @Test
    void createAutomaticPublicMatchLink_shouldReturnExistingLinkWithoutPublishingDuplicate() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);
        match.setRecipientEmail("guest@example.com");
        match.setPublicLinkToken("existing-token");
        match.setPublicLinkRecipientEmail("guest@example.com");
        match.setPublicLinkIssuedAt(LocalDateTime.now().minusMinutes(5));

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        var response = matchService.createAutomaticPublicMatchLink(matchId, null);

        assertTrue(response.isPresent());
        assertEquals("existing-token", response.get().token());
        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(magicLinkService, matchInviteEventPublisher);
    }

    @Test
    void createAutomaticPublicMatchLink_shouldSkipWhenRecipientMissing() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.PENDING);
        ReflectionTestUtils.setField(match, "id", matchId);

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        var response = matchService.createAutomaticPublicMatchLink(matchId, null);

        assertTrue(response.isEmpty());
        verifyNoInteractions(magicLinkService, matchInviteEventPublisher);
    }

    @Test
    void createAutomaticPublicMatchLink_shouldSkipWhenMatchIsNotPending() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        Match match = match(UUID.randomUUID(), UUID.randomUUID(), venueId, MatchStatus.CONFIRMED);
        ReflectionTestUtils.setField(match, "id", matchId);
        match.setRecipientEmail("guest@example.com");

        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));

        var response = matchService.createAutomaticPublicMatchLink(matchId, null);

        assertTrue(response.isEmpty());
        verifyNoInteractions(magicLinkService, matchInviteEventPublisher);
    }

    @Test
    void getPublicFoundItem_shouldReturnFoundItemDetailAndPhotoUrlForMagicLink() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        URI photoUrl = URI.create("/api/found-items/" + foundItemId + "/photo");
        Match match = match(foundItemId, lostReportId, venueId, MatchStatus.PENDING);
        PublicFoundItemResponse foundItem = new PublicFoundItemResponse(
                foundItemId,
                "Schwarzer Rucksack",
                LocalDateTime.of(2026, 5, 12, 14, 30),
                "Neben Buehne 2",
                "STORED",
                new ItemAttributesPayload("Bag", null, "Nike", "Black", List.of("Roter Anhaenger")),
                photoUrl
        );

        when(magicLinkService.verify("public-token", MagicLinkService.TYPE_MATCH_VIEW))
                .thenReturn(new MagicLinkClaims("match_view", matchId, null, venueId, "lost@example.com", 1L));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(foundItemClient.getPublicFoundItemDetail(foundItemId, venueId)).thenReturn(foundItem);

        var response = matchService.getPublicFoundItem("public-token");

        assertTrue(response.isPresent());
        assertEquals(foundItemId, response.get().id());
        assertEquals("Schwarzer Rucksack", response.get().description());
        assertEquals("Bag", response.get().attributes().category());
        assertEquals(URI.create("/api/matches/public/public-token/found-item/photo"), response.get().photoUrl());
        verify(foundItemClient).getPublicFoundItemDetail(foundItemId, venueId);
    }

    @Test
    void getPublicFoundItemPhoto_shouldReturnPhotoForMagicLink() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        Match match = match(foundItemId, lostReportId, venueId, MatchStatus.PENDING);
        RemotePhoto photo = new RemotePhoto("photo-bytes".getBytes(), MediaType.IMAGE_JPEG, 11);

        when(magicLinkService.verify("public-token", MagicLinkService.TYPE_MATCH_VIEW))
                .thenReturn(new MagicLinkClaims("match_view", matchId, null, venueId, "lost@example.com", 1L));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(foundItemClient.getPublicFoundItemPhoto(foundItemId, venueId)).thenReturn(photo);

        var response = matchService.getPublicFoundItemPhoto("public-token");

        assertTrue(response.isPresent());
        assertSame(photo, response.get());
    }

    @Test
    void confirmPublicMatch_shouldUpdateLinkedMatchAndPendingDuplicatesForSamePair() {
        MatchService matchService = matchService();
        UUID matchId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        Match match = match(foundItemId, lostReportId, venueId, MatchStatus.PENDING);

        when(magicLinkService.verify("public-token", MagicLinkService.TYPE_MATCH_VIEW))
                .thenReturn(new MagicLinkClaims("match_view", matchId, null, venueId, "lost@example.com", 1L));
        when(matchRepository.findById(matchId)).thenReturn(Optional.of(match));
        when(matchRepository.save(match)).thenReturn(match);

        var response = matchService.confirmPublicMatch("public-token");

        assertTrue(response.isPresent());
        assertEquals(MatchStatus.CONFIRMED, response.get().status());
        verify(matchRepository).updateStatusForPair(
                lostReportId,
                foundItemId,
                venueId,
                MatchStatus.PENDING,
                MatchStatus.CONFIRMED
        );
    }

    private MatchService matchService() {
        return new MatchService(
                matchRepository,
                venueAccessService,
                foundItemClient,
                lostItemClient,
                magicLinkService,
                matchInviteEventPublisher,
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

    private Match match(
            UUID foundItemId,
            UUID lostReportId,
            UUID venueId,
            MatchStatus status
    ) {
        return new Match(
                foundItemId,
                lostReportId,
                venueId,
                status,
                0.60f,
                0.80f,
                0.72f,
                LocalDateTime.now()
        );
    }
}

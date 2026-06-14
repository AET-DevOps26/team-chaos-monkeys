package com.foundflow.matching.service;

import com.foundflow.magiclink.MagicLinkClaims;
import com.foundflow.magiclink.MagicLinkService;
import com.foundflow.matching.client.FoundItemClient;
import com.foundflow.matching.client.ItemVenueReference;
import com.foundflow.matching.client.LostItemClient;
import com.foundflow.matching.client.LostReportContactReference;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.dto.CreateMatchRequest;
import com.foundflow.matching.dto.CreatePublicMatchLinkRequest;
import com.foundflow.matching.dto.HistogramResponse;
import com.foundflow.matching.dto.MatchResponse;
import com.foundflow.matching.dto.PublicFoundItemResponse;
import com.foundflow.matching.dto.PublicMatchLinkResponse;
import com.foundflow.matching.dto.TimeBucketCount;
import com.foundflow.matching.dto.UpdateMatchRequest;
import com.foundflow.matching.messaging.MatchInviteEventPublisher;
import com.foundflow.matching.repository.BucketCountView;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.security.VenueAccessService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class MatchService {

    private final MatchRepository matchRepository;
    private final VenueAccessService venueAccessService;
    private final FoundItemClient foundItemClient;
    private final LostItemClient lostItemClient;
    private final MagicLinkService magicLinkService;
    private final MatchInviteEventPublisher matchInviteEventPublisher;
    private final String publicBaseUrl;

    public MatchService(
            MatchRepository matchRepository,
            VenueAccessService venueAccessService,
            FoundItemClient foundItemClient,
            LostItemClient lostItemClient,
            MagicLinkService magicLinkService,
            MatchInviteEventPublisher matchInviteEventPublisher,
            @Value("${foundflow.public.base-url}") String publicBaseUrl
    ) {
        this.matchRepository = matchRepository;
        this.venueAccessService = venueAccessService;
        this.foundItemClient = foundItemClient;
        this.lostItemClient = lostItemClient;
        this.magicLinkService = magicLinkService;
        this.matchInviteEventPublisher = matchInviteEventPublisher;
        this.publicBaseUrl = publicBaseUrl;
    }

    public MatchResponse createMatch(
            CreateMatchRequest request,
            Jwt jwt
    ) {
        UUID venueId = validateAndResolveMatchVenue(
                request.foundItemId(),
                request.lostReportId(),
                request.venueId(),
                jwt
        );

        Optional<Match> existing = matchRepository.findFirstByLostReportIdAndFoundItemId(
                request.lostReportId(),
                request.foundItemId()
        );
        if (existing.isPresent()) {
            Match match = existing.get();
            if (match.getStatus() != MatchStatus.PENDING) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Match already exists for this pair with status " + match.getStatus()
                );
            }
            return toResponse(match);
        }

        Match match = new Match(
                request.foundItemId(),
                request.lostReportId(),
                venueId,
                MatchStatus.PENDING,
                request.attributeScore(),
                request.semanticScore(),
                request.combinedScore(),
                LocalDateTime.now()
        );

        Match savedMatch = matchRepository.save(match);
        return toResponse(savedMatch);
    }

    public List<MatchResponse> getAllMatches(
            UUID foundItemId,
            UUID lostReportId,
            MatchStatus status,
            Jwt jwt
    ) {
        return matchRepository.findFiltered(
                        venueFilter(jwt),
                        foundItemId,
                        lostReportId,
                        statusName(status)
                )
                .stream()
                .map(this::toResponse)
                .toList();
    }

    public long countMatches(
            UUID foundItemId,
            UUID lostReportId,
            MatchStatus status,
            Jwt jwt
    ) {
        return countMatches(foundItemId, lostReportId, status, null, jwt);
    }

    public long countMatches(
            UUID foundItemId,
            UUID lostReportId,
            MatchStatus status,
            UUID requestedVenueId,
            Jwt jwt
    ) {
        return matchRepository.countFiltered(
                resolveVenueFilter(requestedVenueId, jwt),
                foundItemId,
                lostReportId,
                statusName(status)
        );
    }

    public HistogramResponse getMatchHistogram(
            UUID foundItemId,
            UUID lostReportId,
            MatchStatus status,
            Jwt jwt
    ) {
        return getMatchHistogram(foundItemId, lostReportId, status, null, jwt);
    }

    public HistogramResponse getMatchHistogram(
            UUID foundItemId,
            UUID lostReportId,
            MatchStatus status,
            UUID requestedVenueId,
            Jwt jwt
    ) {
        List<TimeBucketCount> perDay = toTimeBucketCounts(
                matchRepository.findDailyBuckets(
                        resolveVenueFilter(requestedVenueId, jwt),
                        foundItemId,
                        lostReportId,
                        statusName(status)
                )
        );

        return new HistogramResponse(
                perDay,
                aggregate(perDay, this::weekStart),
                aggregate(perDay, this::monthStart)
        );
    }

    public Optional<MatchResponse> getMatchById(
            UUID id,
            Jwt jwt
    ) {
        return matchRepository.findById(id)
                .map(match -> {
                    verifyVenueAccess(jwt, match.getVenueId());
                    return match;
                })
                .map(this::toResponse);
    }

    public Optional<MatchResponse> updateMatch(
            UUID id,
            UpdateMatchRequest request,
            Jwt jwt
    ) {
        return matchRepository.findById(id)
                .map(match -> {
                    verifyVenueAccess(jwt, match.getVenueId());
                    UUID venueId = validateAndResolveMatchVenue(
                            request.foundItemId(),
                            request.lostReportId(),
                            request.venueId(),
                            jwt
                    );

                    match.setFoundItemId(request.foundItemId());
                    match.setLostReportId(request.lostReportId());
                    match.setVenueId(venueId);
                    match.setStatus(request.status());
                    match.setAttributeScore(request.attributeScore());
                    match.setSemanticScore(request.semanticScore());
                    match.setCombinedScore(request.combinedScore());

                    Match updatedMatch = matchRepository.save(match);
                    return toResponse(updatedMatch);
                });
    }

    public Optional<MatchResponse> getPublicMatch(String token) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_MATCH_VIEW);
        return matchRepository.findById(claims.matchId())
                .filter(match -> match.getVenueId().equals(claims.venueId()))
                .map(this::toResponse);
    }

    public Optional<PublicFoundItemResponse> getPublicFoundItem(String token) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_MATCH_VIEW);
        return matchRepository.findById(claims.matchId())
                .filter(match -> match.getVenueId().equals(claims.venueId()))
                .map(match -> foundItemClient.getPublicFoundItemDetail(
                        match.getFoundItemId(),
                        match.getVenueId()
                ));
    }

    @Transactional
    public Optional<MatchResponse> confirmPublicMatch(String token) {
        return updatePublicMatchStatus(token, MatchStatus.CONFIRMED);
    }

    @Transactional
    public Optional<MatchResponse> rejectPublicMatch(String token) {
        return updatePublicMatchStatus(token, MatchStatus.REJECTED);
    }

    @Transactional
    public Optional<PublicMatchLinkResponse> createPublicMatchLink(
            UUID id,
            CreatePublicMatchLinkRequest request,
            Jwt jwt
    ) {
        return matchRepository.findById(id)
                .map(match -> {
                    verifyVenueAccess(jwt, match.getVenueId());
                    String requestedRecipient = request == null ? null : request.email();
                    String defaultRecipient = hasText(requestedRecipient)
                            ? match.getRecipientEmail()
                            : resolveLostReportContactEmail(match, jwt);
                    String recipient = resolveRecipient(requestedRecipient, defaultRecipient, true);
                    return createPublicMatchLink(match, recipient);
                });
    }

    @Transactional
    public Optional<PublicMatchLinkResponse> createAutomaticPublicMatchLink(
            UUID id,
            String eventRecipientEmail
    ) {
        return matchRepository.findById(id)
                .flatMap(match -> {
                    if (match.getStatus() != MatchStatus.PENDING) {
                        return Optional.empty();
                    }
                    String recipient = resolveRecipient(eventRecipientEmail, match.getRecipientEmail(), false);
                    if (recipient == null) {
                        return Optional.empty();
                    }
                    return Optional.of(createPublicMatchLink(match, recipient));
                });
    }

    private UUID venueFilter(Jwt jwt) {
        return resolveVenueFilter(null, jwt);
    }

    private Optional<MatchResponse> updatePublicMatchStatus(String token, MatchStatus status) {
        MagicLinkClaims claims = magicLinkService.verify(token, MagicLinkService.TYPE_MATCH_VIEW);
        return matchRepository.findById(claims.matchId())
                .filter(match -> match.getVenueId().equals(claims.venueId()))
                .map(match -> {
                    match.setStatus(status);
                    Match savedMatch = matchRepository.save(match);
                    matchRepository.updateStatusForPair(
                            savedMatch.getLostReportId(),
                            savedMatch.getFoundItemId(),
                            savedMatch.getVenueId(),
                            MatchStatus.PENDING,
                            status
                    );
                    return toResponse(savedMatch);
                });
    }

    private UUID resolveVenueFilter(UUID requestedVenueId, Jwt jwt) {
        if (venueAccessService.isAdmin(jwt)) {
            return requestedVenueId;
        }

        UUID jwtVenueId = venueAccessService.getVenueId(jwt);
        if (requestedVenueId != null && !requestedVenueId.equals(jwtVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }

        return jwtVenueId;
    }

    private String statusName(MatchStatus status) {
        return status == null ? null : status.name();
    }

    private String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String resolveLostReportContactEmail(Match match, Jwt jwt) {
        String storedRecipient = normalizeEmail(match.getRecipientEmail());
        if (storedRecipient != null) {
            return storedRecipient;
        }

        LostReportContactReference contact = lostItemClient.getLostReportContact(match.getLostReportId(), jwt);
        if (!match.getVenueId().equals(contact.venueId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Lost item service returned a contact for a different venue."
            );
        }

        String contactEmail = normalizeEmail(contact.contactEmail());
        if (contactEmail != null) {
            match.setRecipientEmail(contactEmail);
            matchRepository.save(match);
        }
        return contactEmail;
    }

    private String resolveRecipient(String requestedEmail, String defaultEmail, boolean required) {
        String normalizedRequested = normalizeEmail(requestedEmail);
        if (normalizedRequested != null) {
            return normalizedRequested;
        }

        String normalizedDefault = normalizeEmail(defaultEmail);
        if (normalizedDefault != null) {
            return normalizedDefault;
        }

        if (required) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "No recipient email is available for this match."
            );
        }
        return null;
    }

    private PublicMatchLinkResponse createPublicMatchLink(Match match, String recipient) {
        String normalizedRecipient = normalizeEmail(recipient);
        if (!Objects.equals(match.getRecipientEmail(), normalizedRecipient)) {
            match.setRecipientEmail(normalizedRecipient);
            matchRepository.save(match);
        }

        String token = magicLinkService.createMatchViewToken(
                match.getId(),
                match.getVenueId(),
                normalizedRecipient
        );
        String matchUrl = publicBaseUrl + "/api/matches/public/" + token;
        matchInviteEventPublisher.publishMatchInviteRequested(
                match.getId(),
                normalizedRecipient,
                match.getVenueId(),
                matchUrl
        );
        return new PublicMatchLinkResponse(
                token,
                matchUrl,
                pickupUrl(token)
        );
    }

    private String pickupUrl(String token) {
        return publicBaseUrl + "/api/pickups/public/" + token;
    }

    private UUID validateAndResolveMatchVenue(
            UUID foundItemId,
            UUID lostReportId,
            UUID requestedVenueId,
            Jwt jwt
    ) {
        ItemVenueReference foundItem = foundItemClient.getFoundItem(foundItemId, jwt);
        ItemVenueReference lostItem = lostItemClient.getLostItem(lostReportId, jwt);

        if (!foundItem.venueId().equals(lostItem.venueId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Found item and lost item must belong to the same venue."
            );
        }

        if (venueAccessService.isAdmin(jwt)
                && requestedVenueId != null
                && !requestedVenueId.equals(foundItem.venueId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Match venueId must match the referenced resources."
            );
        }

        verifyVenueAccess(jwt, foundItem.venueId());

        return foundItem.venueId();
    }

    private void verifyVenueAccess(Jwt jwt, UUID resourceVenueId) {
        if (!venueAccessService.canAccessVenue(jwt, resourceVenueId)) {
            throw new AccessDeniedException("No access to this venue.");
        }
    }

    private List<TimeBucketCount> toTimeBucketCounts(List<BucketCountView> buckets) {
        return buckets.stream()
                .map(bucket -> new TimeBucketCount(bucket.getBucketStart(), bucket.getCount()))
                .toList();
    }

    private List<TimeBucketCount> aggregate(
            List<TimeBucketCount> buckets,
            Function<LocalDate, LocalDate> bucketSelector
    ) {
        Map<LocalDate, Long> groupedBuckets = buckets.stream()
                .collect(Collectors.groupingBy(
                        bucket -> bucketSelector.apply(bucket.bucketStart()),
                        TreeMap::new,
                        Collectors.summingLong(TimeBucketCount::count)
                ));

        return groupedBuckets.entrySet()
                .stream()
                .map(entry -> new TimeBucketCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private LocalDate weekStart(LocalDate date) {
        if (date == null) {
            return null;
        }

        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate monthStart(LocalDate date) {
        if (date == null) {
            return null;
        }

        return date.withDayOfMonth(1);
    }

    private MatchResponse toResponse(Match match) {
        return new MatchResponse(
                match.getId(),
                match.getFoundItemId(),
                match.getLostReportId(),
                match.getVenueId(),
                match.getRecipientEmail(),
                match.getStatus(),
                match.getAttributeScore(),
                match.getSemanticScore(),
                match.getCombinedScore(),
                match.getCreatedAt()
        );
    }
}

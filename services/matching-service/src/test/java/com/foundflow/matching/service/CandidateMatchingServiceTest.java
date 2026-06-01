package com.foundflow.matching.service;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.ItemAttributesPayload;
import com.foundflow.events.LostReportCreatedEvent;
import com.foundflow.events.LostReportUpdatedEvent;
import com.foundflow.genai.client.GenaiClient;
import com.foundflow.genai.client.model.EmbedRequest;
import com.foundflow.genai.client.model.EmbedResponse;
import com.foundflow.matching.domain.ItemEmbedding;
import com.foundflow.matching.domain.ItemType;
import com.foundflow.matching.domain.Match;
import com.foundflow.matching.domain.MatchStatus;
import com.foundflow.matching.messaging.MatchCandidateEventPublisher;
import com.foundflow.matching.repository.ItemEmbeddingRepository;
import com.foundflow.matching.repository.MatchRepository;
import com.foundflow.matching.repository.SimilarItemEmbedding;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CandidateMatchingServiceTest {

    private static final int TOP_K = 20;
    private static final float THRESHOLD = 0.55f;

    private ItemEmbeddingRepository itemEmbeddingRepository;
    private MatchRepository matchRepository;
    private GenaiClient genaiClient;
    private MatchCandidateEventPublisher eventPublisher;
    private CandidateMatchingService service;

    @BeforeEach
    void setUp() {
        itemEmbeddingRepository = mock(ItemEmbeddingRepository.class);
        matchRepository = mock(MatchRepository.class);
        genaiClient = mock(GenaiClient.class);
        eventPublisher = mock(MatchCandidateEventPublisher.class);
        service = new CandidateMatchingService(
                itemEmbeddingRepository,
                matchRepository,
                genaiClient,
                eventPublisher,
                new SimpleMeterRegistry(),
                TOP_K,
                THRESHOLD
        );
    }

    @Test
    void aboveThresholdCandidate_persistsMatchAndPublishesEvent() {
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostReportId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f, 0.0f));
        when(itemEmbeddingRepository.findTopKSimilar(eq(ItemType.FOUND), eq(venueId), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, "Bag", 0.1f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Black leather backpack"));

        ArgumentCaptor<Match> matchCaptor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(matchCaptor.capture());
        Match persisted = matchCaptor.getValue();
        assertThat(persisted.getLostReportId()).isEqualTo(lostReportId);
        assertThat(persisted.getFoundItemId()).isEqualTo(foundItemId);
        assertThat(persisted.getStatus()).isEqualTo(MatchStatus.PENDING);
        assertThat(persisted.getAttributeScore()).isEqualTo(1.0f);
        assertThat(persisted.getSemanticScore()).isEqualTo(0.9f);
        assertThat(persisted.getCombinedScore()).isEqualTo(0.9f);

        verify(eventPublisher).publishMatchCandidateCreated(persisted);
    }

    @Test
    void belowThresholdCandidate_persistsNothing() {
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostReportId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, "Bag", 0.6f))); // semantic=0.4, combined=0.4
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(any(), any()))
                .thenReturn(Optional.empty());

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Backpack"));

        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void categoryMismatch_gatesScoreToZero() {
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostReportId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        // Very high semantic similarity (distance 0.02) but categories differ → combined = 0.0
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, "Wallet", 0.02f)));

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Black backpack"));

        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void unchangedTextSource_skipsEmbeddingAndSearch() {
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        LostReportUpdatedEvent event = new LostReportUpdatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReportId,
                venueId,
                "lost/photo.jpg",
                "Black leather backpack",
                Instant.now(),
                "Front desk",
                "OPEN",
                new ItemAttributesPayload("Bag", null, null, List.of())
        );
        String stored = CandidateMatchingService.buildEmbeddingText(
                event.description(),
                event.attributes()
        );
        when(itemEmbeddingRepository.findTextSource(ItemType.LOST, lostReportId))
                .thenReturn(Optional.of(stored));

        service.findCandidatesForLostReportUpdate(event);

        verifyNoInteractions(genaiClient);
        verify(itemEmbeddingRepository, never()).upsert(any(ItemEmbedding.class));
        verify(itemEmbeddingRepository, never()).findTopKSimilar(any(), any(), any(), any(Integer.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void existingPendingMatch_isUpsertedInPlace_notRecreated() {
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        UUID existingMatchId = UUID.randomUUID();

        Match existing = new Match(foundItemId, lostReportId, venueId, MatchStatus.PENDING,
                1.0f, 0.6f, 0.6f, LocalDateTime.now().minusHours(1));

        when(itemEmbeddingRepository.findTextSource(any(), any())).thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, "Bag", 0.05f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.of(existing));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Bag"));

        verify(matchRepository, times(1)).save(existing);
        assertThat(existing.getCombinedScore()).isEqualTo(0.95f);
        verify(eventPublisher).publishMatchCandidateCreated(existing);
    }

    @Test
    void existingConfirmedMatch_isNeverTouched() {
        UUID lostReportId = UUID.randomUUID();
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        Match confirmed = new Match(foundItemId, lostReportId, venueId, MatchStatus.CONFIRMED,
                1.0f, 0.6f, 0.6f, LocalDateTime.now().minusDays(1));

        when(itemEmbeddingRepository.findTextSource(any(), any())).thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(foundItemId, "Bag", 0.05f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.of(confirmed));

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Bag"));

        verify(matchRepository, never()).save(any(Match.class));
        verifyNoInteractions(eventPublisher);
        assertThat(confirmed.getCombinedScore()).isEqualTo(0.6f); // unchanged
    }

    @Test
    void foundItemEvent_searchesLostSide() {
        UUID foundItemId = UUID.randomUUID();
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(ItemType.FOUND, foundItemId))
                .thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(eq(ItemType.LOST), eq(venueId), any(), eq(TOP_K)))
                .thenReturn(List.of(new SimilarItemEmbedding(lostReportId, "Bag", 0.05f)));
        when(matchRepository.findFirstByLostReportIdAndFoundItemId(lostReportId, foundItemId))
                .thenReturn(Optional.empty());
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        service.findCandidatesForFoundItem(new FoundItemLoggedEvent(
                UUID.randomUUID(), Instant.now(),
                foundItemId, venueId,
                "found/photo.jpg", "Black backpack",
                Instant.now(), "Front desk", "STORED",
                UUID.randomUUID(),
                new ItemAttributesPayload("Bag", null, null, List.of())
        ));

        ArgumentCaptor<Match> captor = ArgumentCaptor.forClass(Match.class);
        verify(matchRepository).save(captor.capture());
        assertThat(captor.getValue().getLostReportId()).isEqualTo(lostReportId);
        assertThat(captor.getValue().getFoundItemId()).isEqualTo(foundItemId);
    }

    @Test
    void blankEmbeddingText_skipsAllWork() {
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();
        // No description, no attributes — embedding text is blank
        LostReportCreatedEvent event = new LostReportCreatedEvent(
                UUID.randomUUID(), Instant.now(),
                lostReportId, venueId, null, null, Instant.now(), null, "OPEN",
                null
        );

        service.findCandidatesForLostReport(event);

        verifyNoInteractions(genaiClient);
        verifyNoInteractions(eventPublisher);
        verify(itemEmbeddingRepository, never()).upsert(any(ItemEmbedding.class));
    }

    @Test
    void lostReportIntake_sendsPurposeLostReportToEmbed() {
        UUID lostReportId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(any(), any())).thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of());

        service.findCandidatesForLostReport(lostReportEvent(lostReportId, venueId, "Bag", "Bag"));

        ArgumentCaptor<EmbedRequest> captor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(genaiClient).embed(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(EmbedRequest.PurposeEnum.LOST_REPORT);
        assertThat(captor.getValue().getTexts()).hasSize(1);
    }

    @Test
    void foundItemIntake_sendsPurposeFoundItemToEmbed() {
        UUID foundItemId = UUID.randomUUID();
        UUID venueId = UUID.randomUUID();

        when(itemEmbeddingRepository.findTextSource(any(), any())).thenReturn(Optional.empty());
        when(genaiClient.embed(any())).thenReturn(embedResponse(1.0f));
        when(itemEmbeddingRepository.findTopKSimilar(any(), any(), any(), eq(TOP_K)))
                .thenReturn(List.of());

        service.findCandidatesForFoundItem(new FoundItemLoggedEvent(
                UUID.randomUUID(), Instant.now(),
                foundItemId, venueId,
                "found/photo.jpg", "Bag",
                Instant.now(), "Front desk", "STORED",
                UUID.randomUUID(),
                new ItemAttributesPayload("Bag", null, null, List.of())
        ));

        ArgumentCaptor<EmbedRequest> captor = ArgumentCaptor.forClass(EmbedRequest.class);
        verify(genaiClient).embed(captor.capture());
        assertThat(captor.getValue().getPurpose()).isEqualTo(EmbedRequest.PurposeEnum.FOUND_ITEM);
    }

    @Test
    void categoryGate_followsRecommendedRules() {
        assertThat(CandidateMatchingService.categoryGate("Bag", "Bag")).isEqualTo(1.0f);
        assertThat(CandidateMatchingService.categoryGate(null, null)).isEqualTo(1.0f);
        assertThat(CandidateMatchingService.categoryGate("Bag", null)).isEqualTo(0.5f);
        assertThat(CandidateMatchingService.categoryGate(null, "Bag")).isEqualTo(0.5f);
        assertThat(CandidateMatchingService.categoryGate("Bag", "Wallet")).isEqualTo(0.0f);
    }

    @Test
    void buildEmbeddingText_includesAllAvailableAttributeFields() {
        String text = CandidateMatchingService.buildEmbeddingText(
                "Black backpack",
                new ItemAttributesPayload("Bag", "Nike", "Black", List.of("red tag", "torn strap"))
        );
        assertThat(text).contains("Black backpack");
        assertThat(text).contains("category: Bag");
        assertThat(text).contains("brand: Nike");
        assertThat(text).contains("color: Black");
        assertThat(text).contains("marks: red tag, torn strap");
    }

    private LostReportCreatedEvent lostReportEvent(UUID lostReportId, UUID venueId, String category, String description) {
        return new LostReportCreatedEvent(
                UUID.randomUUID(),
                Instant.now(),
                lostReportId,
                venueId,
                "lost/photo.jpg",
                description,
                Instant.now(),
                "Front desk",
                "OPEN",
                new ItemAttributesPayload(category, null, null, List.of())
        );
    }

    private static EmbedResponse embedResponse(float... vector) {
        List<Float> boxed = new java.util.ArrayList<>(vector.length);
        for (float v : vector) {
            boxed.add(v);
        }
        EmbedResponse response = new EmbedResponse();
        response.setEmbeddings(List.of(boxed));
        response.setDimensions(vector.length);
        return response;
    }
}

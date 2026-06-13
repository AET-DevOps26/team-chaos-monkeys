package com.foundflow.matching.service;

import com.foundflow.events.FoundItemLoggedEvent;
import com.foundflow.events.FoundItemUpdatedEvent;
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
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class CandidateMatchingService {

    private static final Logger log = LoggerFactory.getLogger(CandidateMatchingService.class);

    private final ItemEmbeddingRepository itemEmbeddingRepository;
    private final MatchRepository matchRepository;
    private final GenaiClient genaiClient;
    private final MatchCandidateEventPublisher eventPublisher;
    private final MatchVerificationService verificationService;
    private final MeterRegistry meterRegistry;

    private final int topK;
    private final float threshold;
    private final float republishScoreDelta;
    private final int expectedEmbeddingDim;

    public CandidateMatchingService(
            ItemEmbeddingRepository itemEmbeddingRepository,
            MatchRepository matchRepository,
            GenaiClient genaiClient,
            MatchCandidateEventPublisher eventPublisher,
            MatchVerificationService verificationService,
            MeterRegistry meterRegistry,
            @Value("${foundflow.matching.top-k:20}") int topK,
            @Value("${foundflow.matching.threshold:0.55}") float threshold,
            @Value("${foundflow.matching.republish-score-delta:0.01}") float republishScoreDelta,
            @Value("${foundflow.matching.embedding-dim:768}") int expectedEmbeddingDim
    ) {
        this.itemEmbeddingRepository = itemEmbeddingRepository;
        this.matchRepository = matchRepository;
        this.genaiClient = genaiClient;
        this.eventPublisher = eventPublisher;
        this.verificationService = verificationService;
        this.meterRegistry = meterRegistry;
        this.topK = topK;
        this.threshold = threshold;
        this.republishScoreDelta = republishScoreDelta;
        this.expectedEmbeddingDim = expectedEmbeddingDim;
    }

    @Transactional
    public void findCandidatesForLostReport(LostReportCreatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.contactEmail(),
                event.description(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForLostReportUpdate(LostReportUpdatedEvent event) {
        processIntake(
                ItemType.LOST,
                event.lostReportId(),
                event.venueId(),
                event.contactEmail(),
                event.description(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForFoundItem(FoundItemLoggedEvent event) {
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
                null,
                event.description(),
                event.attributes()
        );
    }

    @Transactional
    public void findCandidatesForFoundItemUpdate(FoundItemUpdatedEvent event) {
        processIntake(
                ItemType.FOUND,
                event.foundItemId(),
                event.venueId(),
                null,
                event.description(),
                event.attributes()
        );
    }

    void processIntake(
            ItemType itemType,
            UUID itemId,
            UUID venueId,
            String description,
            ItemAttributesPayload attributes
    ) {
        processIntake(itemType, itemId, venueId, null, description, attributes);
    }

    void processIntake(
            ItemType itemType,
            UUID itemId,
            UUID venueId,
            String contactEmail,
            String description,
            ItemAttributesPayload attributes
    ) {
        String embeddingText = buildEmbeddingText(description, attributes);
        if (embeddingText.isBlank()) {
            log.debug("Skipping {} {} — empty embedding text.", itemType, itemId);
            return;
        }

        Optional<String> existing = itemEmbeddingRepository.findTextSource(itemType, itemId);
        if (existing.isPresent() && existing.get().equals(embeddingText)) {
            log.debug("Skipping {} {} — text_source unchanged.", itemType, itemId);
            return;
        }

        String ownCategory = attributes != null ? attributes.category() : null;
        EmbedRequest embedRequest = new EmbedRequest()
                .texts(List.of(embeddingText))
                .purpose(itemType == ItemType.LOST
                        ? EmbedRequest.PurposeEnum.LOST_REPORT
                        : EmbedRequest.PurposeEnum.FOUND_ITEM);
        float[] embedding = Timer.builder("matching.embedding.duration")
                .description("Time to fetch an embedding from genai-service")
                .register(meterRegistry)
                .record(() -> toFloatArray(genaiClient.embed(embedRequest)));

        itemEmbeddingRepository.upsert(new ItemEmbedding(
                UUID.randomUUID(),
                itemType,
                itemId,
                venueId,
                ownCategory,
                itemType == ItemType.LOST ? normalizeEmail(contactEmail) : null,
                embedding,
                embeddingText
        ));

        ItemType oppositeType = itemType == ItemType.LOST ? ItemType.FOUND : ItemType.LOST;
        List<SimilarItemEmbedding> candidates = Timer.builder("matching.search.duration")
                .description("Time to run pgvector similarity search")
                .register(meterRegistry)
                .record(() -> itemEmbeddingRepository.findTopKSimilar(oppositeType, venueId, embedding, topK));

        DistributionSummary scoreHistogram = DistributionSummary.builder("matching.score.combined")
                .description("Distribution of combined match scores")
                .register(meterRegistry);

        for (SimilarItemEmbedding candidate : candidates) {
            float attributeScore = categoryGate(ownCategory, candidate.category());
            float semanticScore = 1.0f - candidate.cosineDistance();
            float combinedScore = attributeScore * semanticScore;
            scoreHistogram.record(combinedScore);

            String decision = combinedScore >= threshold ? "above_threshold" : "below_threshold";
            meterRegistry.counter("matching.candidates.found_total", "decision", decision).increment();

            if (combinedScore < threshold) {
                continue;
            }

            UUID lostReportId = itemType == ItemType.LOST ? itemId : candidate.itemId();
            UUID foundItemId = itemType == ItemType.LOST ? candidate.itemId() : itemId;
            String recipientEmail = itemType == ItemType.LOST ? contactEmail : candidate.contactEmail();

            UpsertedMatch persisted = upsertMatch(
                    lostReportId,
                    foundItemId,
                    venueId,
                    recipientEmail,
                    attributeScore,
                    semanticScore,
                    combinedScore
            );

            if (persisted != null && persisted.shouldPublish()) {
                eventPublisher.publishMatchCandidateCreated(persisted.match());
                String ownText = embeddingText;
                String otherText = candidate.textSource();
                String lostText = itemType == ItemType.LOST ? ownText : otherText;
                String foundText = itemType == ItemType.LOST ? otherText : ownText;
                runAfterCommitOrNow(() -> verificationService.verifyAsync(persisted.match().getId(), lostText, foundText));
            }
        }
    }

    private UpsertedMatch upsertMatch(
            UUID lostReportId,
            UUID foundItemId,
            UUID venueId,
            String recipientEmail,
            float attributeScore,
            float semanticScore,
            float combinedScore
    ) {
        Optional<Match> existing = matchRepository.findFirstByLostReportIdAndFoundItemId(
                lostReportId,
                foundItemId
        );

        if (existing.isPresent()) {
            Match match = existing.get();
            if (match.getStatus() != MatchStatus.PENDING) {
                return null;
            }
            boolean materialScoreChange = scoreChanged(match, attributeScore, semanticScore, combinedScore);
            String normalizedEmail = normalizeEmail(recipientEmail);
            boolean contactChanged = normalizedEmail != null
                    && !Objects.equals(normalizedEmail, match.getRecipientEmail());
            if (!materialScoreChange && !contactChanged) {
                return new UpsertedMatch(match, false);
            }
            if (materialScoreChange) {
                match.setAttributeScore(attributeScore);
                match.setSemanticScore(semanticScore);
                match.setCombinedScore(combinedScore);
            }
            if (contactChanged) {
                match.setRecipientEmail(normalizedEmail);
            }
            return new UpsertedMatch(matchRepository.save(match), materialScoreChange);
        }

        Match fresh = new Match(
                foundItemId,
                lostReportId,
                venueId,
                normalizeEmail(recipientEmail),
                MatchStatus.PENDING,
                attributeScore,
                semanticScore,
                combinedScore,
                LocalDateTime.now()
        );
        return new UpsertedMatch(matchRepository.save(fresh), true);
    }

    private boolean scoreChanged(
            Match match,
            float attributeScore,
            float semanticScore,
            float combinedScore
    ) {
        return Math.abs(match.getAttributeScore() - attributeScore) >= republishScoreDelta
                || Math.abs(match.getSemanticScore() - semanticScore) >= republishScoreDelta
                || Math.abs(match.getCombinedScore() - combinedScore) >= republishScoreDelta;
    }

    private float[] toFloatArray(EmbedResponse response) {
        if (response == null || response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new IllegalStateException("GenAI /embed returned no embeddings.");
        }
        List<Float> first = response.getEmbeddings().get(0);
        if (first.size() != expectedEmbeddingDim) {
            log.error(
                    "GenAI /embed returned {} dimensions but matching expects {}.",
                    first.size(),
                    expectedEmbeddingDim
            );
            throw new IllegalStateException(
                    "Embedding dimension mismatch: expected " + expectedEmbeddingDim + " but got " + first.size()
            );
        }
        float[] vector = new float[first.size()];
        for (int i = 0; i < first.size(); i++) {
            vector[i] = first.get(i);
        }
        return vector;
    }

    static float categoryGate(String own, String candidate) {
        if (Objects.equals(own, candidate)) {
            return 1.0f;
        }
        if (own == null || candidate == null) {
            return 0.5f;
        }
        return 0.0f;
    }

    static String buildEmbeddingText(String description, ItemAttributesPayload attributes) {
        List<String> parts = new ArrayList<>();
        if (description != null && !description.isBlank()) {
            parts.add(description.trim());
        }
        if (attributes != null) {
            if (attributes.category() != null) {
                parts.add("category: " + attributes.category());
            }
            if (attributes.brand() != null) {
                parts.add("brand: " + attributes.brand());
            }
            if (attributes.color() != null) {
                parts.add("color: " + attributes.color());
            }
            if (attributes.marks() != null && !attributes.marks().isEmpty()) {
                parts.add("marks: " + String.join(", ", attributes.marks()));
            }
        }
        return String.join(" | ", parts);
    }

    private static String normalizeEmail(String email) {
        return email == null || email.isBlank() ? null : email.trim().toLowerCase();
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            action.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private record UpsertedMatch(Match match, boolean shouldPublish) {
    }
}
